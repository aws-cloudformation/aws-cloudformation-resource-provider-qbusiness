package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.datasource.Constants.API_CREATE_DATASOURCE;

import java.time.Duration;
import java.util.Objects;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class CreateHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(30))
      .build();

  private final Constant backOffStrategy;

  public CreateHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public CreateHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  private Logger logger;

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    var reqModel = request.getDesiredResourceState();
    logger.log("[INFO] Starting Create Data Source process in stack: %s For Account: %s, Application: %s, Index: %s"
        .formatted(request.getStackId(), request.getAwsAccountId(),
            request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getIndexId()
        )
    );

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataSource::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(
                    request.getClientRequestToken(), model
                ))
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::callCreateDataSource)
                .stabilize((createReq, createResponse, client, model, context) -> isStabilized(request, client, model, logger))
                .handleError((createReq, error, client, model, context) -> handleError(
                    createReq, model, error, context, logger, API_CREATE_DATASOURCE
                ))
                .done(response -> ProgressEvent.progress(reqModel.toBuilder().dataSourceId(response.dataSourceId()).build(), callbackContext))
        )
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
  }

  private boolean isStabilized(
      final ResourceHandlerRequest<ResourceModel> request,
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model,
      Logger logger
  ) {
    logger.log("[INFO] Checking for Create Complete for Data Source process in stack: %s with ID: %s, For Account: %s, Application: %s, Index: %s"
        .formatted(request.getStackId(), request.getAwsAccountId(), model.getDataSourceId(), model.getApplicationId(), model.getIndexId())
    );

    GetDataSourceResponse getDataSourceRes = getDataSource(model, proxyClient);
    var status = getDataSourceRes.status();

    if (DataSourceStatus.ACTIVE.equals(status)) {
      logger.log("[INFO] %s with ID: %s, for App: %s, IndexId: %s, stack ID: %s has stabilized".formatted(
          ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId(), request.getStackId()
      ));

      return true;
    }

    if (!DataSourceStatus.FAILED.equals(status)) {
      logger.log("[INFO] %s with ID: %s, for App: %s, IndexId: %s, stack ID: %s is still stabilizing".formatted(
          ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId(), request.getStackId()
      ));
      return false;
    }

    logger.log("[INFO] %s with ID: %s, for App: %s, IndexId: %s, stack ID: %s has failed to stabilize with message: %s".formatted(
        ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId(), request.getStackId(),
        Objects.nonNull(getDataSourceRes.error()) ? getDataSourceRes.error().errorMessage() : null
    ));

    InternalServerException causeError = null;
    if (Objects.nonNull(getDataSourceRes.error()) && StringUtils.isNotBlank(getDataSourceRes.error().errorMessage())) {
      causeError = InternalServerException.builder().message(getDataSourceRes.error().errorMessage()).build();
    }

    throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getDataSourceId(), causeError);
  }

  private CreateDataSourceResponse callCreateDataSource(
      CreateDataSourceRequest request,
      ProxyClient<QBusinessClient> proxyClient
  ) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::createDataSource);
  }
}
