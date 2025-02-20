package software.amazon.qbusiness.dataaccessor;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
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


  protected GetDataAccessorResponse callGetDataAccessor(GetDataAccessorRequest request, ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::getDataAccessor);
  }

  protected ListTagsForResourceResponse callListTags(ListTagsForResourceRequest request, ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::listTagsForResource);
  }
}
