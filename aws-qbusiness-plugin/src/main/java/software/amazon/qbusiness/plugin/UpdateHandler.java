package software.amazon.qbusiness.plugin;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.plugin.Constants.API_UPDATE_PLUGIN;
import static software.amazon.qbusiness.plugin.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.PluginBuildStatus;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginResponse;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {
  private Logger logger;

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(10))
      .build();
  private final Constant backOffStrategy;

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

    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, PluginId: %s] Entering Update Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getPluginId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Plugin::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::callUpdatePlugin)
                .stabilize((updateReq, updateResponse, client, model, context) -> isStabilized(request, client, model, logger))
                .handleError((describeApplicationRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_UPDATE_PLUGIN
                ))
                .progress())

        .then(progress -> {
          var arn = Utils.buildPluginArn(request, progress.getResourceModel());
          return TagUtils.updateTags(ResourceModel.TYPE_NAME, progress, request, arn, proxyClient, logger);
        })
        .then(progress ->
            new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger)
        );
  }

  private UpdatePluginResponse callUpdatePlugin(UpdatePluginRequest request,
      ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::updatePlugin);
  }

  private boolean isStabilized(
      final ResourceHandlerRequest<ResourceModel> request,
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model,
      Logger logger
  ) {
    logger.log("[INFO] Checking for Update Complete for Plugin process in stack: %s with ID: %s, For Account: %s, Application: %s"
        .formatted(request.getStackId(), model.getPluginId(), request.getAwsAccountId(), model.getApplicationId())
    );

    GetPluginResponse getPluginRes = getPlugin(model, proxyClient);
    var status = getPluginRes.buildStatus();

    if (PluginBuildStatus.READY.equals(status)) {
      logger.log("[INFO] %s with ID: %s, for App: %s, stack ID: %s has stabilized".formatted(
          ResourceModel.TYPE_NAME, model.getPluginId(), model.getApplicationId(), request.getStackId()
      ));

      return true;
    }

    if (PluginBuildStatus.UPDATE_IN_PROGRESS.equals(status)) {
      logger.log("[INFO] %s with ID: %s, for App: %s, stack ID: %s is still stabilizing".formatted(
          ResourceModel.TYPE_NAME, model.getPluginId(), model.getApplicationId(), request.getStackId()
      ));
      return false;
    }

    logger.log("[INFO] %s with ID: %s, for App: %s, stack ID: %s has failed to stabilize".formatted(
        ResourceModel.TYPE_NAME, model.getPluginId(), model.getApplicationId(), request.getStackId()
    ));

    throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getPluginId(), null);
  }

}
