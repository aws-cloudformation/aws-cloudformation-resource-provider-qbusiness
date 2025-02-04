package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.dataaccessor.Constants.API_UPDATE_DATA_ACCESSOR;

import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {

  private Logger logger;
  private final TagHelper tagHelper;

  public UpdateHandler() {
    this(new TagHelper());
  }

  public UpdateHandler(TagHelper tagHelper) {
    this.tagHelper = tagHelper;
  }


  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    this.logger.log(
        "[INFO] - [StackId: %s, ApplicationId: %s, DataAccessorId: %s] Entering Update Handler"
            .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(),
                request.getDesiredResourceState().getDataAccessorId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataAccessor::Update", proxyClient,
                    progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .makeServiceCall(this::callUpdateDataAccessor)
                .handleError((serviceRequest, error, client, model, context) -> handleError(
                    serviceRequest, model, error, context, logger, API_UPDATE_DATA_ACCESSOR
                ))
                .progress())
        .then(progress -> {
          if (!tagHelper.shouldUpdateTags(request)) {
            return progress;
          }

          Map<String, String> tagsToAdd = tagHelper.generateTagsToAdd(
              tagHelper.getPreviouslyAttachedTags(request),
              tagHelper.getNewDesiredTags(request)
          );

          if (tagsToAdd == null || tagsToAdd.isEmpty()) {
            return progress;
          }

          return proxy.initiate("AWS-QBusiness-Plugin::TagResource", proxyClient,
                  progress.getResourceModel(), progress.getCallbackContext())
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

          return proxy.initiate("AWS-QBusiness-Application::UnTagResource", proxyClient,
                  progress.getResourceModel(), progress.getCallbackContext())
              .translateToServiceRequest(
                  model -> Translator.untagResourceRequest(request, model, tagsToRemove))
              .makeServiceCall(this::callUntagResource)
              .progress();
        })
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext,
            proxyClient, logger));
  }

  private UpdateDataAccessorResponse callUpdateDataAccessor(UpdateDataAccessorRequest request,
      ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::updateDataAccessor);
  }

  private TagResourceResponse callTagResource(TagResourceRequest request,
      ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTags()) {
      return TagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::tagResource);
  }

  private UntagResourceResponse callUntagResource(UntagResourceRequest request,
      ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTagKeys()) {
      return UntagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::untagResource);
  }
}
