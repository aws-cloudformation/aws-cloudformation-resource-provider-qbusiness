package software.amazon.qbusiness.permission;

import static software.amazon.qbusiness.permission.Constants.API_GET_POLICY;

import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetPolicyRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPolicyResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.qbusiness.permission.internal.PolicyParser;

public class ListHandler extends BaseHandlerStd {

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<QBusinessClient> proxyClient,
        final Logger logger) {
        logger.log(
            "[INFO] - [StackId: %s, ApplicationId: %s] Entering List Handler"
                .formatted(request.getStackId(),
                    request.getDesiredResourceState().getApplicationId()));

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("AWS-QBusiness-Permission::List", proxyClient,
                        request.getDesiredResourceState(), callbackContext)
                    .translateToServiceRequest(Translator::translateToReadRequest)
                    .makeServiceCall(this::callGetPolicy)
                    .handleError((getApplicationRequest, error, client, model, context) ->
                        handleError(getApplicationRequest, model, error, context, logger,
                            API_GET_POLICY))
                    .done(serviceResponse -> {
                      final Optional<String> policy = serviceResponse.getValueForField("policy", String.class);
                      if(!policy.isPresent()) {
                            logger.log("[ERROR] No policy exists for ApplicationId %s"
                                .formatted(request.getDesiredResourceState().getApplicationId()));

                            throw new CfnInternalFailureException();
                        }
                        final List<ResourceModel> modelsFromPolicy = PolicyParser.getPermissionModelsFromPolicy(policy.get(), request.getDesiredResourceState().getApplicationId());
                        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .status(OperationStatus.SUCCESS)
                            .resourceModels(modelsFromPolicy)
                            .build();
                    })
            );
    }

    private GetPolicyResponse callGetPolicy(GetPolicyRequest request,
        ProxyClient<QBusinessClient> proxyClient) {
        return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::getPolicy);
    }
}
