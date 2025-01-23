package software.amazon.qbusiness.permission;

import static software.amazon.qbusiness.permission.Constants.API_GET_POLICY;

import java.util.Optional;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetPolicyRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPolicyResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.qbusiness.permission.internal.PolicyParser;

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
        "[INFO] - [StackId: %s, ApplicationId: %s, StatementId: %s] Entering Read Handler"
            .formatted(request.getStackId(),
                request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getStatementId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Permisssion::Read", proxyClient,
                    request.getDesiredResourceState(), callbackContext)
                .translateToServiceRequest(Translator::translateToReadRequest)
                .makeServiceCall(this::callGetPolicy)
                .handleError((getApplicationRequest, error, client, model, context) ->
                    handleError(getApplicationRequest, model, error, context, logger,
                        API_GET_POLICY))
                .done(serviceResponse -> {
                  final Optional<String> policy = serviceResponse.getValueForField("policy", String.class);
                  if (!policy.isPresent()) {
                    logger.log("[ERROR] No policy exists for ApplicationId %s"
                        .formatted(request.getDesiredResourceState().getApplicationId()));
                    throw new CfnInternalFailureException();
                  }
                  final Optional<ResourceModel> modelFromPolicy =
                      PolicyParser.getStatementFromPolicy(policy.get(), request.getDesiredResourceState().getStatementId(),
                          request.getDesiredResourceState().getApplicationId());

                  if (!modelFromPolicy.isPresent()) {
                    throw new CfnNotFoundException(ResourceModel.TYPE_NAME, request.getDesiredResourceState().getStatementId());
                  }
                  return ProgressEvent.defaultSuccessHandler(modelFromPolicy.get());
                })
        );
  }

  private GetPolicyResponse callGetPolicy(GetPolicyRequest request,
      ProxyClient<QBusinessClient> proxyClient) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::getPolicy);
  }
}
