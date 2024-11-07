package software.amazon.qbusiness.application;

import static software.amazon.qbusiness.application.Constants.API_CREATE_APPLICATION;
import static software.amazon.qbusiness.application.Constants.API_UPDATE_APPLICATION;
import static software.amazon.qbusiness.application.Constants.AUTOSUBSCRIBE_FIELD_VALIDATION_ERROR;

import java.time.Duration;
import java.util.Objects;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.ApplicationStatus;
import software.amazon.awssdk.services.qbusiness.model.AutoSubscriptionStatus;
import software.amazon.awssdk.services.qbusiness.model.CreateApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateApplicationResponse;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationResponse;
import software.amazon.awssdk.services.qbusiness.model.IdentityType;
import software.amazon.awssdk.services.qbusiness.model.SubscriptionType;
import software.amazon.awssdk.services.qbusiness.model.UpdateApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateApplicationResponse;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class CreateHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(5))
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
    logger.log("[INFO] Starting to process Create Application request in stack: %s for Account: %s"
        .formatted(request.getStackId(), request.getAwsAccountId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Application::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(request.getClientRequestToken(), model, request.getSystemTags()))
                .backoffDelay(backOffStrategy)
                .makeServiceCall((awsRequest, clientProxyClient) -> callCreateApplication(awsRequest, clientProxyClient, progress.getResourceModel()))
                .stabilize((awsReq, response, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model, logger))
                .handleError((createReq, error, client, model, context) ->
                    handleError(createReq, model, error, context, logger, API_CREATE_APPLICATION))
                .progress()
        ).then(progress -> {
            if (!isIAMFederatedApp(IdentityType.fromValue(request.getDesiredResourceState().getIdentityType()))) {
              return progress;
            }
            // Immediately update the application to add auto-subscribe configuration to it.
            // TODO: Remove after AutoSubscribeConfiguration is added to the CreateApplication API.
            return proxy.initiate("AWS-QBusiness-Application::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToPostCreateUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::callUpdateApplication)
                .stabilize((awsReq, response, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model, logger))
                .handleError((updateReq, error, client, model, context) ->
                        handleError(updateReq, model, error, context, logger, API_UPDATE_APPLICATION))
                .progress();
            }
        ).then(progress ->
            new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger)
        );
  }

  private void validateAutoSubscriptionConfiguration(ResourceModel desiredResourceState) {
    if (isIAMFederatedApp(IdentityType.fromValue(desiredResourceState.getIdentityType()))) {
      AutoSubscriptionConfiguration config = desiredResourceState.getAutoSubscriptionConfiguration();
      if (config != null && AutoSubscriptionStatus.ENABLED.toString().equals(config.getAutoSubscribe()) &&
              config.getDefaultSubscriptionType()!= null) {
        return;
      }
      throw new CfnInvalidRequestException(String.format(AUTOSUBSCRIBE_FIELD_VALIDATION_ERROR, desiredResourceState.getIdentityType()));
    }
  }

  private boolean isIAMFederatedApp(IdentityType identityType) {
    return IdentityType.AWS_IAM_IDP_OIDC.equals(identityType) || IdentityType.AWS_IAM_IDP_SAML.equals(identityType);
  }

  private boolean isStabilized(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model,
      Logger logger
  ) {
    GetApplicationResponse getAppResponse = getApplication(model, proxyClient, logger);

    var status = getAppResponse.statusAsString();

    if (ApplicationStatus.ACTIVE.toString().equals(status)) {
      logger.log("[INFO] %s with ID: %s has stabilized".formatted(ResourceModel.TYPE_NAME, model.getApplicationId()));
      return true;
    }

    if (!ApplicationStatus.FAILED.toString().equals(status)) {
      logger.log("[INFO] %s with ID: %s is still stabilizing.".formatted(ResourceModel.TYPE_NAME, model.getApplicationId()));
      return false;
    }

    // handle failed state

    RuntimeException causeMessage = null;
    if (Objects.nonNull(getAppResponse.error()) && StringUtils.isNotBlank(getAppResponse.error().errorMessage())) {
      causeMessage = new RuntimeException(getAppResponse.error().errorMessage());
    }

    throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getApplicationId(), causeMessage);
  }

  private CreateApplicationResponse callCreateApplication(CreateApplicationRequest request,
                                                          ProxyClient<QBusinessClient> proxyClient,
                                                          ResourceModel model) {
    validateAutoSubscriptionConfiguration(model);
    var client = proxyClient.client();
    CreateApplicationResponse response = proxyClient.injectCredentialsAndInvokeV2(request, client::createApplication);
    model.setApplicationId(response.applicationId());
    return response;
  }

  private UpdateApplicationResponse callUpdateApplication(UpdateApplicationRequest request,
                                                          ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::updateApplication);
  }
}
