package software.amazon.qbusiness.index;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.IndexStatus;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.Objects;

import static software.amazon.qbusiness.index.Constants.API_CREATE_INDEX;

public class CreateHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofMinutes(2))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

  public CreateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public CreateHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Starting to process Create Index request in stack: %s for Account: %s and ApplicationId: %s"
        .formatted(request.getStackId(), request.getAwsAccountId(), request.getDesiredResourceState().getApplicationId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Index::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(request.getClientRequestToken(), model))
                .backoffDelay(backOffStrategy)
                .makeServiceCall((awsRequest, clientProxyClient) -> callCreateIndex(awsRequest, clientProxyClient, progress.getResourceModel()))
                .stabilize((awsReq, response, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model, logger))
                .handleError((createReq, error, client, model, context) ->
                    handleError(createReq, model, error, context, logger, API_CREATE_INDEX))
                .progress()
        ).then(progress ->
            new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger)
        );
  }

  private boolean isStabilized(
      final ProxyClient<QBusinessClient> proxyClient,
      final ResourceModel model,
      final Logger logger) {
    final GetIndexResponse getIndexResponse = getIndex(model, proxyClient, logger);

    final String status = getIndexResponse.statusAsString();

    if (IndexStatus.ACTIVE.toString().equals(status)) {
      logger.log("[INFO] %s with ApplicationId: %s and IndexId: %s has stabilized"
          .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getIndexId()));
      return true;
    }

    if (!IndexStatus.FAILED.toString().equals(status)) {
      logger.log("[INFO] %s with ApplicationId: %s and IndexId: %s is still stabilizing."
          .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getIndexId()));
      return false;
    }

    // handle failed state

    RuntimeException causeMessage = null;
    if (Objects.nonNull(getIndexResponse.error()) && StringUtils.isNotBlank(getIndexResponse.error().errorMessage())) {
      causeMessage = new RuntimeException(getIndexResponse.error().errorMessage());
    }

    throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier().toString(), causeMessage);
  }

  private CreateIndexResponse callCreateIndex(final CreateIndexRequest request,
                                              final ProxyClient<QBusinessClient> proxyClient,
                                              final ResourceModel model) {
    CreateIndexResponse response = proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::createIndex);
    model.setIndexId(response.indexId());
    return response;
  }
}
