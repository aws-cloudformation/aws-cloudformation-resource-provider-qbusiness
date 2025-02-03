package software.amazon.qbusiness.webexperience;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.webexperience.Constants.API_UPDATE_WEB_EXPERIENCE;
import static software.amazon.qbusiness.webexperience.Utils.primaryIdentifier;

import java.time.Duration;
import java.util.Objects;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
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
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {

  public static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(2))
      .delay(Duration.ofSeconds(5))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

  public UpdateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public UpdateHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Starting Update for %s with ApplicationId: %s and WebExperienceId: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getApplicationId(),
        request.getDesiredResourceState().getWebExperienceId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-WebExperience::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::updateWebExperience)
                .stabilize((serviceRequest, updateWebExperienceResponse, client, model, context) -> isStabilized(client, model))
                .handleError((serviceRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_UPDATE_WEB_EXPERIENCE
                ))
                .progress()
        )
        .then(progress -> {
          var arn = Utils.buildWebExperienceArn(request, progress.getResourceModel());
          return TagUtils.updateTags(ResourceModel.TYPE_NAME, progress, request, arn, proxyClient, logger);
        })
        .then(model -> readHandler(proxy, request, callbackContext, proxyClient));
  }

  private ProgressEvent<ResourceModel, CallbackContext> readHandler(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient) {
    return new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger);
  }

  private UpdateWebExperienceResponse updateWebExperience(
      final UpdateWebExperienceRequest request,
      final ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::updateWebExperience);
  }

  private boolean isStabilized(
      final ProxyClient<QBusinessClient> proxyClient,
      final ResourceModel model) {
    final GetWebExperienceResponse getWebExperienceResponse = getWebExperience(model, proxyClient, logger);
    final WebExperienceStatus status = getWebExperienceResponse.status();
    final String roleArn = getWebExperienceResponse.roleArn();

    if (WebExperienceStatus.ACTIVE.equals(status)) {
      logger.log("[INFO] %s with ApplicationId: %s and WebExperienceId: %s has stabilized."
              .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getWebExperienceId()));
      return true;
    }

    if (roleArn == null && WebExperienceStatus.PENDING_AUTH_CONFIG.equals(status)) {
      logger.log("[INFO] %s with ApplicationId: %s and WebExperienceId: %s has stabilized."
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
}
