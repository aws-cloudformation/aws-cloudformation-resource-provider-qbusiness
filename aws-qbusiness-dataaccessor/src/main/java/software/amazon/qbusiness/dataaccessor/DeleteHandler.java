package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.dataaccessor.Constants.API_DELETE_DATA_ACCESSOR;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataAccessorResponse;
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
    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, DataAccessorId: %s] Entering Delete Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getDataAccessorId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataAccessor::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .makeServiceCall(this::callDeleteDataAccessor)
                .handleError((awsRequest, error, clientProxyClient, model, context) -> handleError(
                    awsRequest, model, error, context, logger, API_DELETE_DATA_ACCESSOR
                ))
                .progress()
        )
        .then(progress -> ProgressEvent.defaultSuccessHandler(null));
  }

  private DeleteDataAccessorResponse callDeleteDataAccessor(DeleteDataAccessorRequest request,
      ProxyClient<QBusinessClient> proxyClient) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::deleteDataAccessor);
  }
}
