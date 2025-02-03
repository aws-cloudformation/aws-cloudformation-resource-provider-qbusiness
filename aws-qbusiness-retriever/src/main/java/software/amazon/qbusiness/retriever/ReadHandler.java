package software.amazon.qbusiness.retriever;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.retriever.Constants.API_GET_RETRIEVER;
import static software.amazon.qbusiness.retriever.Utils.primaryIdentifier;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverResponse;
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
    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, RetrieverId: %s] Entering Read Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getRetrieverId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Retriever::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall(this::callGetRetriever)
                .handleError((getRetrieverRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_GET_RETRIEVER
                ))
                .done(serviceResponse -> ProgressEvent.progress(Translator.translateFromReadResponse(serviceResponse), callbackContext))
        )
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Retriever::ListTags",
                    proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext()
                )
                .translateToServiceRequest(model -> Translator.translateToListTagsRequest(request, model))
                .makeServiceCall(this::callListTags)
                .handleError((listTagsRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_GET_RETRIEVER
                ))
                .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                        Translator.translateFromReadResponseWithTags(listTagsResponse, progress.getResourceModel())
                    )
                )
        );
  }

  protected GetRetrieverResponse callGetRetriever(GetRetrieverRequest request, ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::getRetriever);
  }
}
