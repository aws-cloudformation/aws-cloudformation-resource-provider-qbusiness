package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.dataaccessor.Constants.API_GET_DATA_ACCESSOR;

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
        this.logger.log(
            "[INFO] - [StackId: %s, ApplicationId: %s, DataAccessorId: %s] Entering Read Handler"
                .formatted(request.getStackId(),
                    request.getDesiredResourceState().getApplicationId(),
                    request.getDesiredResourceState().getDataAccessorId()));

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-QBusiness-DataAccessor::Read", proxyClient,
                        request.getDesiredResourceState(), callbackContext)
                    .translateToServiceRequest(Translator::translateToReadRequest)
                    .makeServiceCall(this::callGetDataAccessor)
                    .handleError((getApplicationRequest, error, client, model, context) ->
                        handleError(getApplicationRequest, model, error, context, logger,
                            API_GET_DATA_ACCESSOR))
                    .done(serviceResponse -> ProgressEvent.progress(
                        Translator.translateFromReadResponse(serviceResponse), callbackContext))
            )
            .then(progress ->
                proxy.initiate("AWS-QBusiness-DataAccessor::ListTags",
                        proxyClient, progress.getResourceModel(),
                        progress.getCallbackContext()
                    )
                    .translateToServiceRequest(
                        model -> Translator.translateToListTagsRequest(request, model))
                    .makeServiceCall(this::callListTags)
                    .handleError((listTagsRequest, error, client, model, context) ->
                        handleError(listTagsRequest, model, error, context, logger,
                            API_GET_DATA_ACCESSOR))
                    .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                            Translator.translateFromReadResponseWithTags(listTagsResponse,
                                progress.getResourceModel())
                        )
                    )
            );
    }
}
