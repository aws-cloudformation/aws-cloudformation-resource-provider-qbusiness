package software.amazon.qbusiness.permission;

import static software.amazon.qbusiness.permission.Constants.API_ASSOCIATE_PERMISSION;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AssociatePermissionRequest;
import software.amazon.awssdk.services.qbusiness.model.AssociatePermissionResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandler extends BaseHandlerStd {

  private Logger logger;

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;
    this.logger.log(
        "[INFO] - [StackId: %s, ApplicationId: %s] Entering Create Handler"
            .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Permission::Create", proxyClient,
                    progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToCreateRequest)
                .makeServiceCall((awsRequest, client) -> callAssociatePermission(awsRequest, client,
                    progress.getResourceModel()))
                .handleError((associatePermissionRequest, error, client, model, context) ->
                    handleError(associatePermissionRequest, model, error, context, logger,
                        API_ASSOCIATE_PERMISSION))
                .progress()
        )
        .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
  }

  private AssociatePermissionResponse callAssociatePermission(
      AssociatePermissionRequest request,
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model) {
    logger.log("[DEBUG] Calling service with request: %s".formatted(request));

    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request,
        client::associatePermission);
  }
}
