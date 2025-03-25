package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.datasource.Constants.API_CREATE_DATASOURCE;
import static software.amazon.qbusiness.datasource.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceResponse;
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

    logger.log("[INFO] Starting Create Data Source process in stack: %s For Account: %s, Application: %s, Index: %s"
        .formatted(request.getStackId(), request.getAwsAccountId(),
            request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getIndexId()
        )
    );

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataSource::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(
                    request, model
                ))
                .backoffDelay(backOffStrategy)
                .makeServiceCall((awsRequest, clientProxyClient) -> callCreateDataSource(awsRequest, clientProxyClient, progress.getResourceModel()))
                .stabilize((createReq, createResponse, client, model, context) -> isCreatingOrUpdateStabilized(API_CREATE_DATASOURCE, request, client, model, logger))
                .handleError((createReq, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_CREATE_DATASOURCE
                ))
                .progress()
        )
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
  }

  private CreateDataSourceResponse callCreateDataSource(
      CreateDataSourceRequest request,
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    final CreateDataSourceResponse response = proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::createDataSource);
    model.setDataSourceId(response.dataSourceId());
    return response;
  }
}
