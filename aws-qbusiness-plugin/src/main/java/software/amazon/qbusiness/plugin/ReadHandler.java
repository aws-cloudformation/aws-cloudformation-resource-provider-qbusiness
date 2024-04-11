package software.amazon.qbusiness.plugin;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetPluginRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static software.amazon.qbusiness.plugin.Constants.API_GET_PLUGIN;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<QBusinessClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, PluginId: %s] Entering Read Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getPluginId()));

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
          .then(progress ->
              proxy.initiate("AWS-QBusiness-Plugin::Read", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                  .translateToServiceRequest(Translator::translateToReadRequest)
                  .makeServiceCall(this::callGetPlugin)
                  .handleError((getRetrieverRequest, error, client, model, context) ->
                      handleError(getRetrieverRequest, model, error, context, logger, API_GET_PLUGIN))
                  .done(serviceResponse -> ProgressEvent.progress(Translator.translateFromReadResponse(serviceResponse), callbackContext))
          )
          .then(progress ->
              proxy.initiate("AWS-QBusiness-Plugin::ListTags",
                      proxyClient, progress.getResourceModel(),
                      progress.getCallbackContext()
                  )
                  .translateToServiceRequest(model -> Translator.translateToListTagsRequest(request, model))
                  .makeServiceCall(this::callListTags)
                  .handleError((listTagsRequest, error, client, model, context) ->
                      handleError(listTagsRequest, model, error, context, logger, API_GET_PLUGIN))
                  .done(listTagsResponse -> ProgressEvent.defaultSuccessHandler(
                          Translator.translateFromReadResponseWithTags(listTagsResponse, progress.getResourceModel())
                      )
                  )
          );
    }
}
