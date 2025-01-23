package software.amazon.qbusiness.index;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.index.Constants.API_UPDATE_INDEX;
import static software.amazon.qbusiness.index.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.IndexStatus;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {

  public static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(2))
      .delay(Duration.ofMinutes(1))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

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

    logger.log("[INFO] Starting Update for %s with ApplicationId: %s and IndexId: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getApplicationId(),
        request.getDesiredResourceState().getIndexId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Index::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::updateIndex)
                .stabilize((serviceRequest, updateIndexResponse, client, model, context) -> isStabilized(client, model))
                .handleError((serviceRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_UPDATE_INDEX
                ))
                .progress()
        )
        .then(progress -> {
          var arn = Utils.buildIndexArn(request, progress.getResourceModel());
          return TagUtils.updateTags(ResourceModel.TYPE_NAME, progress, request, arn, proxyClient, logger);
        })
        .then(model -> readHandler(proxy, request, callbackContext, proxyClient));
  }

  private ProgressEvent<ResourceModel, CallbackContext> readHandler(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient) {
    return new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger);
  }

  private UpdateIndexResponse updateIndex(final UpdateIndexRequest request, final ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::updateIndex);
  }

  private boolean isStabilized(
      final ProxyClient<QBusinessClient> proxyClient,
      final ResourceModel model) {
    GetIndexResponse getIndexResponse = getIndex(model, proxyClient, logger);
    final IndexStatus status = getIndexResponse.status();
    final boolean hasStabilized = IndexStatus.ACTIVE.equals(status);
    logger.log("[INFO] %s with ApplicationId: %s and IndexId: %s has stabilized."
        .formatted(ResourceModel.TYPE_NAME, model.getApplicationId(), model.getIndexId()));
    return hasStabilized;
  }
}
