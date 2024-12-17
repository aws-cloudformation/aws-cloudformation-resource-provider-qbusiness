package software.amazon.qbusiness.plugin;

import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.PluginBuildStatus;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginResponse;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static software.amazon.qbusiness.plugin.Constants.API_UPDATE_PLUGIN;

public class UpdateHandler extends BaseHandlerStd {
  private Logger logger;

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
          .timeout(Duration.ofHours(4))
          .delay(Duration.ofSeconds(10))
          .build();
  private final Constant backOffStrategy;
private final TagHelper tagHelper;

  public UpdateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY, new TagHelper());
  }

  public UpdateHandler(Constant backOffStrategy, TagHelper tagHelper) {

    this.backOffStrategy = backOffStrategy;
    this.tagHelper = tagHelper;

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
                            .handleError((describeApplicationRequest, error, client, model, context) ->
                                    handleError(describeApplicationRequest, model, error, context, logger, API_UPDATE_PLUGIN))
                            .progress())

            .then(progress -> {
              if (!tagHelper.shouldUpdateTags(request)) {
                return progress;
              }

              Map<String, String> tagsToAdd = tagHelper.generateTagsToAdd(
                  tagHelper.getPreviouslyAttachedTags(request),
                  tagHelper.getNewDesiredTags(request)
              );

              if (tagsToAdd == null || tagsToAdd.isEmpty()) {
                return progress;
              }

              return proxy.initiate("AWS-QBusiness-Plugin::TagResource", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                  .translateToServiceRequest(model -> {
                    logger.log("Calling tag resource for plugin:%s in app: %s".formatted(model.getPluginId(), model.getApplicationId()));
                    return Translator.tagResourceRequest(request, model, tagsToAdd);
                  })
                  .makeServiceCall(this::callTagResource)
                  .progress();
            })
            .then(progress -> {
              Set<String> tagsToRemove = tagHelper.generateTagsToRemove(
                  tagHelper.getPreviouslyAttachedTags(request),
                  tagHelper.getNewDesiredTags(request)
              );

              if (CollectionUtils.isEmpty(tagsToRemove)) {
                return progress;
              }

          return proxy.initiate("AWS-QBusiness-Application::UnTagResource", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
              .translateToServiceRequest(model -> Translator.untagResourceRequest(request, model, tagsToRemove))
              .makeServiceCall(this::callUntagResource)
              .progress();
        })
            .then(progress ->
                    new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger)
            );
  }

  private UpdatePluginResponse callUpdatePlugin(UpdatePluginRequest request,
                                                ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::updatePlugin);
  }

  private TagResourceResponse callTagResource(TagResourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTags()) {
      return TagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::tagResource);
  }

  private UntagResourceResponse callUntagResource(UntagResourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    if (!request.hasTagKeys()) {
      return UntagResourceResponse.builder().build();
    }
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::untagResource);
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
