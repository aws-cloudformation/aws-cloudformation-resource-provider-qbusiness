package software.amazon.qbusiness.permission;

import static software.amazon.qbusiness.permission.Constants.API_DISASSOCIATE_PERMISSION;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DisassociatePermissionRequest;
import software.amazon.awssdk.services.qbusiness.model.DisassociatePermissionResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {

  private Logger logger;

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;
    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, StatementId: %s] Entering Delete Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getStatementId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Permission::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .makeServiceCall(this::callDisAssociatePermission)
                .handleError((awsRequest, error, clientProxyClient, model, context) -> handleError(
                    awsRequest, model, error, context, logger, API_DISASSOCIATE_PERMISSION
                ))
                .progress()
        )
        .then(progress -> ProgressEvent.defaultSuccessHandler(null));
  }

  private DisassociatePermissionResponse callDisAssociatePermission(
      DisassociatePermissionRequest request,
      ProxyClient<QBusinessClient> proxyClient) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::disassociatePermission);
  }
}
