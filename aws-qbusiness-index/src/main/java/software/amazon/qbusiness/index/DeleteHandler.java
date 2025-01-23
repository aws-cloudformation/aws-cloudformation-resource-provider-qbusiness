package software.amazon.qbusiness.index;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DeleteIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.index.Constants.API_DELETE_INDEX;
import static software.amazon.qbusiness.index.Utils.primaryIdentifier;

public class DeleteHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(30))
      .build();

  private final Constant backOffStrategy;
  private Logger logger;

  public DeleteHandler() {
    this(DEFAULT_BACK_OFF_STRATEGY);
  }

  public DeleteHandler(Constant backOffStrategy) {
    this.backOffStrategy = backOffStrategy;
  }

  protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
      final AmazonWebServicesClientProxy proxy,
      final ResourceHandlerRequest<ResourceModel> request,
      final CallbackContext callbackContext,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger) {

    this.logger = logger;

    logger.log("[INFO] Initiating delete for %s with ApplicationId: %s and IndexId: %s in stack: %s".formatted(
        ResourceModel.TYPE_NAME,
        request.getDesiredResourceState().getApplicationId(),
        request.getDesiredResourceState().getIndexId(),
        request.getStackId()
    ));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Index::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::callDeleteIndex)
                .stabilize((awsRequest, deleteResponse, clientProxyClient, model, context) -> isStabilized(clientProxyClient, model))
                // See contract tests: https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract.html
                // If the resource did not exist before the delete call, a not found is expected.
                .handleError((awsRequest, error, clientProxyClient, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_DELETE_INDEX
                ))
                .done(deleteResponse -> ProgressEvent.defaultSuccessHandler(null))
        );
  }

  private DeleteIndexResponse callDeleteIndex(DeleteIndexRequest request, ProxyClient<QBusinessClient> proxyClient) {
    var client = proxyClient.client();
    return proxyClient.injectCredentialsAndInvokeV2(request, client::deleteIndex);
  }

  private boolean isStabilized(
      ProxyClient<QBusinessClient> proxyClient,
      ResourceModel model
  ) {
    try {
      getIndex(model, proxyClient, logger);
      // we got a result from Get Index, therefore deletion is still processing.
      return false;
    } catch (ResourceNotFoundException e) {
      logger.log("[Info] Deletion of %s with id: %s has stabilized.".formatted(ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
      return true;
    }
  }
}
