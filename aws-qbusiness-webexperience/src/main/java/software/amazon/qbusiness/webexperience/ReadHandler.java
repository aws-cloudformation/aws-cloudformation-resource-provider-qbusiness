package software.amazon.qbusiness.webexperience;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.webexperience.Constants.API_GET_WEB_EXPERIENCE;
import static software.amazon.qbusiness.webexperience.Utils.primaryIdentifier;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
  private Logger logger;

  @Override
  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, WebExperienceId: %s] Entering Read Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(),
            request.getDesiredResourceState().getWebExperienceId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-WebExperience::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                // Create Get WebExperience request from resource model
                .translateToServiceRequest(Translator::translateToReadRequest)
                // Make call to the service
                .makeServiceCall(this::callGetWebExperience)
                .handleError((getWebExperienceRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_GET_WEB_EXPERIENCE
                ))
                .done(serviceResponse -> ProgressEvent.progress(Translator.translateFromReadResponse(serviceResponse), callbackContext))
        )
        // Now process listing tags for the resource
        .then(progress ->
            proxy.initiate("AWS-QBusiness-WebExperience::ListTags", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToListTagsRequest(request, model))
                .makeServiceCall(this::callListTags)
                .handleError((listTagsRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_GET_WEB_EXPERIENCE
                ))
                .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                        Translator.translateFromReadResponseWithTags(listTagsResponse, progress.getResourceModel())
                    )
                )
        );
  }

  private GetWebExperienceResponse callGetWebExperience(final GetWebExperienceRequest request, final ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::getWebExperience);
  }
}
