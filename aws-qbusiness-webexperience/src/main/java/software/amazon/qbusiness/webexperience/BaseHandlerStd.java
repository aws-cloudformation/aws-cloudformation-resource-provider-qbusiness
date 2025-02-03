package software.amazon.qbusiness.webexperience;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
    final ResourceHandlerRequest<ResourceModel> request,
    final CallbackContext callbackContext,
    final Logger logger) {
    return handleRequest(
      proxy,
      request,
      callbackContext != null ? callbackContext : new CallbackContext(),
      proxy.newProxy(ClientBuilder::getClient),
      logger
    );
  }

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
    final ResourceHandlerRequest<ResourceModel> request,
    final CallbackContext callbackContext,
    final ProxyClient<QBusinessClient> proxyClient,
    final Logger logger);

  protected ListTagsForResourceResponse callListTags(ListTagsForResourceRequest request, ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::listTagsForResource);
  }

  protected GetWebExperienceResponse getWebExperience(ResourceModel model, ProxyClient<QBusinessClient> proxyClient, Logger logger) {
    if (StringUtils.isBlank(model.getApplicationId()) || StringUtils.isBlank(model.getWebExperienceId())) {
      logger.log("[ERROR] Unexpected call to get web experience with a null or empty application ID %s or web experience ID: %s"
          .formatted(model.getApplicationId(), model.getWebExperienceId()));
      throw new NullPointerException();
    }

    GetWebExperienceRequest getWebExperienceRequest = Translator.translateToReadRequest(model);
    return proxyClient.injectCredentialsAndInvokeV2(getWebExperienceRequest, proxyClient.client()::getWebExperience);
  }
}
