package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.common.SharedConstants.API_LIST_TAGS;
import static software.amazon.qbusiness.datasource.Constants.API_GET_DATASOURCE;
import static software.amazon.qbusiness.datasource.Utils.primaryIdentifier;

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

    this.logger.log("[INFO] - [StackId: %s, Resource: %s, PrimaryId: %s, Application: %s, Index: %s] Entering Read Handler"
        .formatted(request.getStackId(), ResourceModel.TYPE_NAME,
            request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getApplicationId(),
            request.getDesiredResourceState().getIndexId()
        ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataSource::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall(this::callGetDataSource)
                .handleError((getDataSourceRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_GET_DATASOURCE
                ))
                .done(response -> ProgressEvent.progress(Translator.translateFromReadResponse(response), callbackContext))
        )
        .then(progress ->
            proxy.initiate(
                    "AWS-QBusiness-DataSource::ListTags",
                    proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext()
                )
                .translateToServiceRequest(model -> Translator.translateToListTagsRequest(request, model))
                .makeServiceCall(this::callListTags)
                .handleError((listTagsReq, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_LIST_TAGS
                ))
                .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                    Translator.translateFromReadResponseWithTags(listTagsResponse, progress.getResourceModel())
                ))
        );
  }
}
