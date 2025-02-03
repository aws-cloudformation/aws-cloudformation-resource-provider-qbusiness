package software.amazon.qbusiness.retriever;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.retriever.Constants.API_DELETE_RETRIEVER;
import static software.amazon.qbusiness.retriever.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DeleteRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteRetrieverResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

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
    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, RetrieverId: %s] Entering Delete Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getRetrieverId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Retriever::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToDeleteRequest)
                .makeServiceCall(this::callDeleteRetriever)
                .handleError((deleteRetrieverRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_DELETE_RETRIEVER
                ))
                .progress()
        )
        .then(progress -> ProgressEvent.defaultSuccessHandler(null));
  }

  protected DeleteRetrieverResponse callDeleteRetriever(DeleteRetrieverRequest request, ProxyClient<QBusinessClient> client) {
    return client.injectCredentialsAndInvokeV2(request, client.client()::deleteRetriever);
  }
}
