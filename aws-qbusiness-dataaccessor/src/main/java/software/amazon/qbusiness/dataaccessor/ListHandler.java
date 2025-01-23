package software.amazon.qbusiness.dataaccessor;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsRequest;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.List;

public class ListHandler extends BaseHandlerStd {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<QBusinessClient> proxyClient,
        final Logger logger) {

        logger.log("[INFO] - [StackId: %s, ApplicationId: %s] Entering List Handler"
            .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId()));

        final ListDataAccessorsRequest listDataAccessorsRequest = Translator.translateToListRequest(
            request.getDesiredResourceState(),
            request.getNextToken());

        ListDataAccessorsResponse listDataAccessorsResponse = proxy.injectCredentialsAndInvokeV2(
            listDataAccessorsRequest, proxyClient.client()::listDataAccessors);

        String nextToken = listDataAccessorsResponse.nextToken();

        List<ResourceModel> models = Translator.translateFromListResponse(
            listDataAccessorsResponse, request.getDesiredResourceState().getApplicationId());

        return ProgressEvent.<ResourceModel, CallbackContext>builder()
            .resourceModels(models)
            .nextToken(nextToken)
            .status(OperationStatus.SUCCESS)
            .build();
    }
}
