package software.amazon.qbusiness.application;

import static software.amazon.qbusiness.application.Constants.API_UPDATE_APPLICATION;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.ApplicationStatus;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationResponse;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateApplicationResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {

  public static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(2))
      .delay(Duration.ofMinutes(2))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

  public UpdateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public UpdateHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Starting Update for %s with id: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getApplicationId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Application::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::updateApplication)
                .stabilize((serviceRequest, updateApplicationResponse, client, model, context) -> isStabilized(client, model))
                .handleError((serviceRequest, error, client, model, context) -> handleError(
                    serviceRequest, model, error, context, logger, API_UPDATE_APPLICATION
                ))
                .progress()
        )
        .then(progress -> {
          var previousTags = TagUtils.getPreviouslyAttachedTags(
              Translator.cfnTagsToGenericMap(request.getPreviousResourceState().getTags()), request);
          var desiredTags = TagUtils.getNewDesiredTags(
              Translator.cfnTagsToGenericMap(request.getDesiredResourceState().getTags()), request);

          if (!TagUtils.shouldUpdateTags(previousTags, desiredTags)) {
            // No updates to tags needed, return early with get application. Since ReadHandler will return Done, this will be the last step
            return readHandler(proxy, request, callbackContext, proxyClient, logger);
          }

          Map<String, String> tagsToAdd = TagUtils.generateTagsToAdd(
              previousTags,
              desiredTags
          );

          if (tagsToAdd == null || tagsToAdd.isEmpty()) {
            return progress;
          }

          return proxy.initiate("AWS-QBusiness-Application::TagResource", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
              .translateToServiceRequest(model -> Translator.tagResourceRequest(request, model, tagsToAdd))
              .makeServiceCall(this::callTagResource)
              .progress();
        })
        .then(progress -> {
          Set<String> tagsToRemove = TagUtils.generateTagsToRemove(
              TagUtils.getPreviouslyAttachedTags(Translator.cfnTagsToGenericMap(request.getPreviousResourceState().getTags()), request),
              TagUtils.getNewDesiredTags(Translator.cfnTagsToGenericMap(request.getDesiredResourceState().getTags()), request)
          );

          if (CollectionUtils.isEmpty(tagsToRemove)) {
            return progress;
          }

          return proxy.initiate("AWS-QBusiness-Application::UnTagResource", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
              .translateToServiceRequest(model -> Translator.untagResourceRequest(request, model, tagsToRemove))
              .makeServiceCall(this::callUntagResource)
              .progress();
        })
        .then(model -> readHandler(proxy, request, callbackContext, proxyClient, logger));
  }

  private ProgressEvent<ResourceModel, CallbackContext> readHandler(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger
  ) {
    return new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger);
  }

  private UpdateApplicationResponse updateApplication(UpdateApplicationRequest request, ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::updateApplication);
  }

  private boolean isStabilized(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    GetApplicationResponse getAppResponse = getApplication(model, proxyClient, logger);
    var status = getAppResponse.status();
    var hasStabilized = ApplicationStatus.ACTIVE.equals(status);
    logger.log("[INFO] %s with ID: %s has stabilized: %s.".formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), hasStabilized));
    return hasStabilized;
  }

  private TagResourceResponse callTagResource(TagResourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTags()) {
      return TagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::tagResource);
  }

  private UntagResourceResponse callUntagResource(UntagResourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTagKeys()) {
      return UntagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::untagResource);
  }
}
