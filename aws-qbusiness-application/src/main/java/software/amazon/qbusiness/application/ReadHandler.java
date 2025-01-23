package software.amazon.qbusiness.application;

import static software.amazon.qbusiness.application.Constants.API_CREATE_APPLICATION;
import static software.amazon.qbusiness.application.Constants.API_GET_APPLICATION;
import static software.amazon.qbusiness.application.Utils.primaryIdentifier;
import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.common.SharedConstants.API_LIST_TAGS;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
  private Logger logger;

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    this.logger.log("[INFO] - [StackId: %s, PrimaryId: %s] Entering Read Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId()));
    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Application::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                // Create Get Application request from resource model
                .translateToServiceRequest(Translator::translateToReadRequest)
                // Make call to the service
                .makeServiceCall(this::callGetApplication)
                .handleError((getApplicationRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_GET_APPLICATION
                ))
                .done(serviceResponse -> ProgressEvent.progress(Translator.translateFromReadResponse(serviceResponse), callbackContext))
        )
        // Now process listing tags for the resource
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Application::ListTags",
                    proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext()
                )
                .translateToServiceRequest(model -> Translator.translateToListTagsRequest(request, model))
                .makeServiceCall(this::callListTags)
                .handleError((listTagsRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_LIST_TAGS
                ))
                .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                        Translator.translateFromReadResponseWithTags(listTagsResponse, progress.getResourceModel())
                    )
                )
        );
  }
}
