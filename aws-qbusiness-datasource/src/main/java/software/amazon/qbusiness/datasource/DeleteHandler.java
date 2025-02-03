package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.datasource.Constants.API_DELETE_DATASOURCE;
import static software.amazon.qbusiness.datasource.Utils.primaryIdentifier;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

@RequiredArgsConstructor
public class DeleteHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_SYNCING_WAIT_BACKOFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(24))
      .delay(Duration.ofMinutes(1))
      .build();

  private final Constant deletionBackOffStrategy;

  private Logger logger;

  public DeleteHandler() {
    this(DEFAULT_SYNCING_WAIT_BACKOFF_STRATEGY);
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    var desiredResourceState = request.getDesiredResourceState();
    logger.log("[INFO] Initiating delete of %s in Stack: %s for ID: %s, application: %s, index: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getStackId(),
        desiredResourceState.getDataSourceId(),
        desiredResourceState.getApplicationId(),
        desiredResourceState.getIndexId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-DataSource::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .backoffDelay(deletionBackOffStrategy)
                .makeServiceCall(this::callDeleteDataSource)
                .stabilize((deleteReq, deleteRes, client, model, context) -> isDoneDeleting(client, model))
                .handleError((deleteReq, error, clientProxyClient, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_DELETE_DATASOURCE
                ))
                .done(deleteResponse -> ProgressEvent.defaultSuccessHandler(null))
        );
  }

  private boolean isDoneDeleting(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    try {
      getDataSource(model, proxyClient);
      logger.log("[INFO] Delete of %s still stabilizing for Resource id: %s, application: %s, index: %s"
          .formatted(ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId()));
      return false;
    } catch (ResourceNotFoundException e) {
      logger.log("[INFO] Delete process of %s has stabilized for Resource id: %s, application: %s, index: %s"
          .formatted(ResourceModel.TYPE_NAME, model.getDataSourceId(), model.getApplicationId(), model.getIndexId()));
      return true;
    }
  }

  private DeleteDataSourceResponse callDeleteDataSource(
      final DeleteDataSourceRequest request,
      final ProxyClient<QBusinessClient> proxyClient
  ) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::deleteDataSource);
  }
}
