package software.amazon.qbusiness.webexperience;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.ErrorDetail;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.WebExperienceStatus;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.Objects;

import static software.amazon.qbusiness.webexperience.Constants.API_CREATE_WEB_EXPERIENCE;
import static software.amazon.qbusiness.webexperience.Constants.API_UPDATE_WEB_EXPERIENCE;

public class CreateHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofMinutes(2))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

  public CreateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public CreateHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Starting to process Create WebExperience request in stack: %s for Account: %s and ApplicationId: %s"
        .formatted(request.getStackId(), request.getAwsAccountId(), request.getDesiredResourceState().getApplicationId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-WebExperience::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(request.getClientRequestToken(), model))
                .backoffDelay(backOffStrategy)
                .makeServiceCall((awsRequest, clientProxyClient) ->
                    callCreateWebExperience(awsRequest, clientProxyClient, progress.getResourceModel()))
                .stabilize((awsReq, response, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model, logger))
                .handleError((createReq, error, client, model, context) ->
                    handleError(createReq, model, error, context, logger, API_CREATE_WEB_EXPERIENCE))
                .progress()
        )
        .then(progress -> {
          // if authentication configuration was not set, let's short circuit
          if (request.getDesiredResourceState().getAuthenticationConfiguration() == null) {
            return readHandler(proxy, request, callbackContext, proxyClient);
          }

          // customer has set an authentication configuration, let's set up a call to Update.
          return proxy.initiate("AWS-QBusiness-WebExperience::PostCreateUpdate",
                  proxyClient, progress.getResourceModel(), progress.getCallbackContext()
              )
              .translateToServiceRequest(Translator::translateToPostCreateUpdateRequest)
              .backoffDelay(backOffStrategy)
              .makeServiceCall(this::callUpdateWebExperience)
              .stabilize((awsReq, response, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model, logger))
              .handleError((createReq, error, client, model, context) ->
                  handleError(createReq, model, error, context, logger, API_UPDATE_WEB_EXPERIENCE))
              .progress();
        })
        .then(progress -> readHandler(proxy, request, callbackContext, proxyClient));
  }

  private ProgressEvent<ResourceModel, CallbackContext> readHandler(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient) {
    return new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger);
  }

  private boolean isStabilized(
      final ProxyClient<QBusinessClient> proxyClient,
      final ResourceModel model,
      final Logger logger) {
    final GetWebExperienceResponse getWebExperienceResponse = getWebExperience(model, proxyClient, logger);

    final String status = getWebExperienceResponse.statusAsString();

    if (WebExperienceStatus.ACTIVE.toString().equals(status)) {
      logger.log("[INFO] %s with ApplicationId: %s and WebExperienceId: %s has stabilized"
          .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getWebExperienceId()));
      return true;
    }

    if (!WebExperienceStatus.FAILED.toString().equals(status)) {
      logger.log("[INFO] %s with ApplicationId: %s and WebExperienceId: %s is still stabilizing."
          .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getWebExperienceId()));
      return false;
    }

    // handle failed state

    RuntimeException causeMessage = null;
    ErrorDetail error = getWebExperienceResponse.error();
    if (Objects.nonNull(error) && StringUtils.isNotBlank(error.errorMessage())) {
      causeMessage = new RuntimeException(error.errorMessage());
    }

    throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier().toString(), causeMessage);
  }

  private CreateWebExperienceResponse callCreateWebExperience(
      final CreateWebExperienceRequest request,
      final ProxyClient<QBusinessClient> proxyClient,
      final ResourceModel model) {
    CreateWebExperienceResponse response = proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::createWebExperience);
    model.setWebExperienceId(response.webExperienceId());
    return response;
  }

  private UpdateWebExperienceResponse callUpdateWebExperience(
      UpdateWebExperienceRequest updateReq,
      final ProxyClient<QBusinessClient> proxyClient
  ) {
    return proxyClient.injectCredentialsAndInvokeV2(updateReq, proxyClient.client()::updateWebExperience);
  }
}
