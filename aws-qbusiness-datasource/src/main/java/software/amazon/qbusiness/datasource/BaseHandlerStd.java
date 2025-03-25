package software.amazon.qbusiness.datasource;

import java.util.Objects;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
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

  protected GetDataSourceResponse getDataSource(ResourceModel model, ProxyClient<QBusinessClient> proxyClient) {
    var request = GetDataSourceRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .dataSourceId(model.getDataSourceId())
        .build();
    return callGetDataSource(request, proxyClient);
  }

  protected boolean isCreatingOrUpdateStabilized(
      final String operation,
      final ResourceHandlerRequest<ResourceModel> request,
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model,
      Logger logger
  ) {
    logger.log("[INFO] Checking for %s Complete for Data Source process in stack: %s with ID: %s, For Account: %s, Application: %s, Index: %s"
        .formatted(operation, request.getStackId(), request.getAwsAccountId(), model.getDataSourceId(), model.getApplicationId(), model.getIndexId())
    );

    GetDataSourceResponse getDataSourceRes = getDataSource(model, proxyClient);
    var status = getDataSourceRes.status();

    if (DataSourceStatus.ACTIVE.equals(status)) {
      logger.log("[INFO] %s for %s with ID: %s, for App: %s, IndexId: %s, stack ID: %s has stabilized".formatted(
          operation, ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId(), request.getStackId()
      ));

      return true;
    }

    /*
    if it's FAILED, then throw not stabilized error

    if it's CREATING or UPDATING, then check if the error object is present and has non-empty error string.
    if so, then throw not stabilized error

    otherwise, return false to indicate not yet stabilized.
     */
    final InternalServerException failureCause;
    if (Objects.nonNull(getDataSourceRes.error()) && StringUtils.isNotBlank(getDataSourceRes.error().errorMessage())) {
      failureCause = InternalServerException.builder().message(getDataSourceRes.error().errorMessage()).build();
    } else {
      failureCause = null;
    }

    if (DataSourceStatus.FAILED.equals(status)) {
      logger.log("[INFO] %s for %s with ID: %s, for App: %s, IndexId: %s, stack ID: %s has failed to stabilize with message: %s".formatted(
          operation, ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId(), request.getStackId(),
          Objects.nonNull(getDataSourceRes.error()) ? getDataSourceRes.error().errorMessage() : null
      ));

      throwNonStableError(model.getDataSourceId(), failureCause);
    }

    if ((DataSourceStatus.CREATING.equals(status) || DataSourceStatus.UPDATING.equals(status)) && failureCause != null) {
      logger.log("[ERROR] %s failed for %s with ID: %s in Status: %s, in App: %s, Index: %s, Stack: %s".formatted(
          operation, ResourceModel.TYPE_NAME, model.getDataSourceId(), status,
          model.getApplicationId(), model.getIndexId(), request.getStackId()
      ));

      throwNonStableError(model.getDataSourceId(), failureCause);
    }

    logger.log("[INFO] %s for %s with id: %s, in app: %s, Index: %s, Stack: %s is still stabilizing with status: %s".formatted(
        operation, ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId(),
        request.getStackId(), status
    ));

    return false;
  }

  private void throwNonStableError(final String resourceId, final Throwable cause) {
    if (cause == null) {
      throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, resourceId);
    } else {
      throw new CfnNotStabilizedException(cause);
    }
  }

  protected GetDataSourceResponse callGetDataSource(GetDataSourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::getDataSource);
  }
}
