package software.amazon.qbusiness.datasource;

import java.util.Optional;

import org.json.JSONObject;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.QBusinessRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
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

  protected GetDataSourceResponse callGetDataSource(GetDataSourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::getDataSource);
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleError(
      QBusinessRequest QBusinessRequest,
      ResourceModel resourceModel,
      Exception error,
      CallbackContext context,
      Logger logger,
      String apiName
  ) throw Exception {
    logger.log("[ERROR] Failed Request: %s to API: %s. Error Message: %s".formatted(QBusinessRequest, apiName, error.getMessage()));
    BaseHandlerException cfnException;

    var primaryIdentifier = Optional.ofNullable(resourceModel)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");

    if (error instanceof ValidationException) {
      cfnException = new CfnInvalidRequestException(error);
    } else if (error instanceof ConflictException) {
      cfnException = new CfnResourceConflictException(error);
    } else if (error instanceof ResourceNotFoundException) {
      cfnException = new CfnNotFoundException(ResourceModel.TYPE_NAME, primaryIdentifier, error);
    } else if (error instanceof ServiceQuotaExceededException) {
      cfnException = new CfnServiceLimitExceededException(error);
    } else if (error instanceof ThrottlingException) {
      cfnException = new CfnThrottlingException(apiName, error);
    } else if (error instanceof AccessDeniedException) {
      cfnException = new CfnAccessDeniedException(apiName, error);
    } else {
      throw error;
    }

    return ProgressEvent.failed(resourceModel, context, cfnException.getErrorCode(), cfnException.getMessage());
  }
}
