package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.dataaccessor.Constants.API_CREATE_DATA_ACCESSOR;
import static software.amazon.qbusiness.dataaccessor.Utils.primaryIdentifier;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorResponse;
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
        "[INFO] - [StackId: %s, ApplicationId: %s, DataAccessorId: %s] Entering Create Handler"
            .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(),
                request.getDesiredResourceState().getDataAccessorId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataAccessor::Create", proxyClient,
                    progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(request, model))
                .makeServiceCall((awsRequest, client) -> callCreateDataAccessor(awsRequest, client,
                    progress.getResourceModel()))
                .handleError((createDataAccessorRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_CREATE_DATA_ACCESSOR
                ))
                .progress()
        )
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext,
            proxyClient, logger));
  }

  private CreateDataAccessorResponse callCreateDataAccessor(
      CreateDataAccessorRequest request,
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model) {
    logger.log("[DEBUG] Calling service with request: %s".formatted(request));

    var client = proxyClient.client();
    CreateDataAccessorResponse response = proxyClient.injectCredentialsAndInvokeV2(request,
        client::createDataAccessor);
    model.setDataAccessorId(response.dataAccessorId());
    return response;
  }
}
