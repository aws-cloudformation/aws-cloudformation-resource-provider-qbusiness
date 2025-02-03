package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.datasource.Constants.API_UPDATE_DATASOURCE;
import static software.amazon.qbusiness.datasource.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataSourceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {

  public static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
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

    logger.log("[INFO] Starting Update for %s with ID: %s, ApplicationId: %s and IndexId: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getDataSourceId(),
        request.getDesiredResourceState().getApplicationId(),
        request.getDesiredResourceState().getIndexId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataSource::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::updateDataSource)
                .stabilize((updateReq, updateRes, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model))
                .handleError((updateReq, error, clientProxyClient, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_UPDATE_DATASOURCE
                ))
                .progress()
        )
        .then(progress -> {
          var arn = Utils.buildDataSourceArn(request, progress.getResourceModel());
          return TagUtils.updateTags(ResourceModel.TYPE_NAME, progress, request, arn, proxyClient, logger);
        })
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
  }

  private UpdateDataSourceResponse updateDataSource(UpdateDataSourceRequest request, ProxyClient<QBusinessClient> proxyClient) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::updateDataSource);
  }

  private boolean isStabilized(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    GetDataSourceResponse getDataSourceResponse = getDataSource(model, proxyClient);
    var status = getDataSourceResponse.status();
    var hasStabilized = DataSourceStatus.ACTIVE.equals(status);

    logger.log("[INFO] Update has %s for %s with ID: %s, ApplicationId: %s and IndexId: %s".formatted(
        hasStabilized ? "stabilized" : "not stabilized yet",
        ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId()));

    return hasStabilized;
  }
}
