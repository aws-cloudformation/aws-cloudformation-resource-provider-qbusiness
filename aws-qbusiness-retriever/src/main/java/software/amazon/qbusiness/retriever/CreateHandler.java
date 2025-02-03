package software.amazon.qbusiness.retriever;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.retriever.Constants.API_CREATE_RETRIEVER;
import static software.amazon.qbusiness.retriever.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateRetrieverResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class CreateHandler extends BaseHandlerStd {

  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(5))
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
    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, RetrieverId: %s] Entering Create Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getRetrieverId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Retriever::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToCreateRequest(request, model))
                .backoffDelay(backOffStrategy)
                .makeServiceCall((awsRequest, clientProxyClient) -> callCreateRetriever(awsRequest, clientProxyClient, progress.getResourceModel()))
                .handleError((createRetrieverRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_CREATE_RETRIEVER
                ))
                .progress()
        )
        .then(progress ->
            new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger)
        );
  }

  private CreateRetrieverResponse callCreateRetriever(CreateRetrieverRequest request,
      ProxyClient<QBusinessClient> client,
      ResourceModel model) {
    CreateRetrieverResponse response = client.injectCredentialsAndInvokeV2(request, client.client()::createRetriever);
    model.setRetrieverId(response.retrieverId());
    return response;
  }
}
