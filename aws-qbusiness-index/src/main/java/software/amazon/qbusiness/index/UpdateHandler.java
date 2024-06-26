package software.amazon.qbusiness.index;

import org.apache.commons.collections4.CollectionUtils;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.IndexStatus;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static software.amazon.qbusiness.index.Constants.API_UPDATE_INDEX;

public class UpdateHandler extends BaseHandlerStd {

  public static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(2))
      .delay(Duration.ofMinutes(1))
      .build();

  private final Constant backOffStrategy;
  private final TagHelper tagHelper;
  private Logger logger;

  public UpdateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY, new TagHelper());
  }

  public UpdateHandler(Constant backOffStrategy, TagHelper tagHelper) {
    this.backOffStrategy = backOffStrategy;
    this.tagHelper = tagHelper;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Starting Update for %s with ApplicationId: %s and IndexId: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getApplicationId(),
        request.getDesiredResourceState().getIndexId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Index::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::updateIndex)
                .stabilize((serviceRequest, updateIndexResponse, client, model, context) -> isStabilized(client, model))
                .handleError((serviceRequest, error, client, model, context) -> handleError(
                    serviceRequest, model, error, context, logger, API_UPDATE_INDEX
                ))
                .progress()
        )
        .then(progress -> {
          if (!tagHelper.shouldUpdateTags(request)) {
            // No updates to tags needed, return early with get application. Since ReadHandler will return Done, this will be the last step
            return readHandler(proxy, request, callbackContext, proxyClient);
          }

          Map<String, String> tagsToAdd = tagHelper.generateTagsToAdd(
              tagHelper.getPreviouslyAttachedTags(request),
              tagHelper.getNewDesiredTags(request)
          );

          if (tagsToAdd == null || tagsToAdd.isEmpty()) {
            return progress;
          }

          return proxy.initiate(
              "AWS-QBusiness-Index::TagResource", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
              .translateToServiceRequest(model -> Translator.tagResourceRequest(request, model, tagsToAdd))
              .makeServiceCall(this::callTagResource)
              .progress();
        })
        .then(progress -> {
          Set<String> tagsToRemove = tagHelper.generateTagsToRemove(
              tagHelper.getPreviouslyAttachedTags(request),
              tagHelper.getNewDesiredTags(request)
          );

          if (CollectionUtils.isEmpty(tagsToRemove)) {
            return progress;
          }

          return proxy.initiate(
              "AWS-QBusiness-Index::UnTagResource", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
              .translateToServiceRequest(model -> Translator.untagResourceRequest(request, model, tagsToRemove))
              .makeServiceCall(this::callUntagResource)
              .progress();
        })
        .then(model -> readHandler(proxy, request, callbackContext, proxyClient));
  }

  private ProgressEvent<ResourceModel, CallbackContext> readHandler(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient) {
    return new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger);
  }

  private UpdateIndexResponse updateIndex(final UpdateIndexRequest request, final ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::updateIndex);
  }

  private boolean isStabilized(
      final ProxyClient<QBusinessClient> proxyClient,
      final ResourceModel model) {
    GetIndexResponse getIndexResponse = getIndex(model, proxyClient, logger);
    final IndexStatus status = getIndexResponse.status();
    final boolean hasStabilized = IndexStatus.ACTIVE.equals(status);
    logger.log("[INFO] %s with ApplicationId: %s and IndexId: %s has stabilized."
        .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getIndexId()));
    return hasStabilized;
  }

  private TagResourceResponse callTagResource(final TagResourceRequest request, final ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTags()) {
      return TagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::tagResource);
  }

  private UntagResourceResponse callUntagResource(final UntagResourceRequest request, final ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTagKeys()) {
      return UntagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::untagResource);
  }
}
