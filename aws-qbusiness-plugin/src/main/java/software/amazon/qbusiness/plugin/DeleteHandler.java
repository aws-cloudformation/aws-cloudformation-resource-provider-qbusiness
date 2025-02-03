package software.amazon.qbusiness.plugin;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.plugin.Constants.API_DELETE_PLUGIN;
import static software.amazon.qbusiness.plugin.Utils.primaryIdentifier;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DeletePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.DeletePluginResponse;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.PluginBuildStatus;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<QBusinessClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, PluginId: %s] Entering Delete Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getPluginId()));

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Retriever::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .makeServiceCall(this::callDeleteRetriever)
                .stabilize((deleteReq, deleteRes, client, model, context) -> isDoneDeleting(client, model))
                .handleError((deleteRetrieverRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_DELETE_PLUGIN
                ))
                .progress()
        )
        .then(progress -> ProgressEvent.defaultSuccessHandler(null));
  }

  protected DeletePluginResponse callDeleteRetriever(DeletePluginRequest request, ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::deletePlugin);
  }

  private boolean isDoneDeleting(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    try {
      GetPluginResponse getPluginRes = getPlugin(model, proxyClient);
      var status = getPluginRes.buildStatus();
      if (!PluginBuildStatus.DELETE_FAILED.equals(status)) {
        logger.log("[INFO] Delete of %s still stabilizing for Resource id: %s, application: %s"
            .formatted(ResourceModel.TYPE_NAME, model.getPluginId(), model.getApplicationId()));
        return false;
      } else {
        logger.log("[INFO] %s with ID: %s, for App: %s, has failed to stabilize".formatted(
            ResourceModel.TYPE_NAME, model.getPluginId(), model.getApplicationId()
        ));
        throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getPluginId(), null);
      }
    } catch (ResourceNotFoundException e) {
      logger.log("[INFO] Delete process of %s has stabilized for Resource id: %s, application: %s"
          .formatted(ResourceModel.TYPE_NAME, model.getPluginId(), model.getApplicationId()));
      return true;
    }
  }

}
