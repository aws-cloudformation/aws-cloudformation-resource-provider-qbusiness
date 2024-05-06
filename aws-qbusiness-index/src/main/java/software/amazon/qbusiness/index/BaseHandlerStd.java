package software.amazon.qbusiness.index;

import java.util.Optional;

import org.json.JSONObject;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.QBusinessRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.awssdk.utils.StringUtils;
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

  protected GetIndexResponse getIndex(ResourceModel model, ProxyClient<QBusinessClient> proxyClient, Logger logger) {
    if (StringUtils.isBlank(model.getApplicationId()) || StringUtils.isBlank(model.getIndexId())) {
      logger.log("[ERROR] Unexpected call to get index with a null or empty application ID %s or index ID: %s"
          .formatted(model.getApplicationId(), model.getIndexId()));
      throw new NullPointerException();
    }

    GetIndexRequest getIndexRequest = Translator.translateToReadRequest(model);
    return proxyClient.injectCredentialsAndInvokeV2(getIndexRequest, proxyClient.client()::getIndex);
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleError(
      final QBusinessRequest qBusinessRequest,
      final ResourceModel resourceModel,
      final Exception error,
      final CallbackContext context,
      final Logger logger,
      final String apiName) {

    logger.log("[ERROR] Failed Request: %s. Error Message: %s".formatted(apiName, error.getMessage()));
    BaseHandlerException cfnException;

    var primaryIdentifier = Optional.ofNullable(resourceModel)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");

    if (error instanceof ResourceNotFoundException) {
      cfnException = new CfnNotFoundException(ResourceModel.TYPE_NAME, primaryIdentifier, error);
    } else if (error instanceof ValidationException) {
      cfnException = new CfnInvalidRequestException(error);
    } else if (error instanceof ThrottlingException) {
      cfnException = new CfnThrottlingException(apiName, error);
    } else if (error instanceof ConflictException) {
      cfnException = new CfnResourceConflictException(error);
    } else if (error instanceof AccessDeniedException) {
      cfnException = new CfnAccessDeniedException(apiName, error);
    } else if (error instanceof ServiceQuotaExceededException) {
      cfnException = new CfnServiceLimitExceededException(error);
    } else {
      cfnException = new CfnGeneralServiceException(error);
    }

    return ProgressEvent.failed(resourceModel, context, cfnException.getErrorCode(), cfnException.getMessage());
  }
}
