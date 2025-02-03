package software.amazon.qbusiness.webexperience;


import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DeleteWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.webexperience.Constants.API_DELETE_WEB_EXPERIENCE;
import static software.amazon.qbusiness.webexperience.Utils.primaryIdentifier;

public class DeleteHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(5))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

  public DeleteHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public DeleteHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Initiating delete for %s with ApplicationId: %s and WebExperienceId: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getApplicationId(),
        request.getDesiredResourceState().getWebExperienceId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-WebExperience::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::callDeleteWebExperience)
                .stabilize((awsRequest, deleteResponse, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model))
                // See contract tests: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract.html
                // If the resource did not exist before the delete call, a not found is expected.
                .handleError((awsRequest, error, clientProxyClient, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_DELETE_WEB_EXPERIENCE
                ))
                .done(deleteResponse -> ProgressEvent.defaultSuccessHandler(null))
        );
  }

  private DeleteWebExperienceResponse callDeleteWebExperience(DeleteWebExperienceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::deleteWebExperience);
  }

  private boolean isStabilized(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    try {
      getWebExperience(model, proxyClient, logger);
      // we got a result from Get WebExperience, therefore deletion is still processing.
      return false;
    } catch (ResourceNotFoundException e) {
      logger.log("[Info] Deletion of %s with id: %s has stabilized.".formatted(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
      return true;
    }
  }
}
