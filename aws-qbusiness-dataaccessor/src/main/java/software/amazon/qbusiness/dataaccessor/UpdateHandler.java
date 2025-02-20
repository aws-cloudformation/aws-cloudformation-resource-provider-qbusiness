package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.dataaccessor.Constants.API_UPDATE_DATA_ACCESSOR;
import static software.amazon.qbusiness.dataaccessor.Utils.primaryIdentifier;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {

  private Logger logger;

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
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_UPDATE_DATA_ACCESSOR
                ))
                .progress()
        )
        .then(progress -> {
          var arn = Utils.buildDataAccessorArn(request, progress.getResourceModel());
          return TagUtils.updateTags(ResourceModel.TYPE_NAME, progress, request, arn, proxyClient, logger);
        })
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext,
            proxyClient, logger));
  }

  private UpdateDataAccessorResponse callUpdateDataAccessor(UpdateDataAccessorRequest request,
      ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::updateDataAccessor);
  }
}
