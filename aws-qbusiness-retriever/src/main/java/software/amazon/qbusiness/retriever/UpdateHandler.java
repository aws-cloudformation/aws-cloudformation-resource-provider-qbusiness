package software.amazon.qbusiness.retriever;

import static software.amazon.qbusiness.common.ErrorUtils.handleError;
import static software.amazon.qbusiness.retriever.Constants.API_UPDATE_RETRIEVER;
import static software.amazon.qbusiness.retriever.Utils.primaryIdentifier;

import java.time.Duration;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.UpdateRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateRetrieverResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;
import software.amazon.qbusiness.common.TagUtils;

public class UpdateHandler extends BaseHandlerStd {
  private static final Constant DEFAULT_BACK_OFF_STRATEGY = Constant.of()
      .timeout(Duration.ofHours(4))
      .delay(Duration.ofSeconds(10))
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
    this.logger.log("[INFO] - [StackId: %s, ApplicationId: %s, RetrieverId: %s] Entering Update Handler"
        .formatted(request.getStackId(), request.getDesiredResourceState().getApplicationId(), request.getDesiredResourceState().getRetrieverId()));

    return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
        .then(progress ->
            proxy.initiate("AWS-QBusiness-Retriever::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .backoffDelay(backOffStrategy)
                .makeServiceCall(this::callUpdateRetriever)
                .handleError((serviceRequest, error, client, model, context) -> handleError(
                    model, primaryIdentifier(model), error, context, logger, ResourceModel.TYPE_NAME, API_UPDATE_RETRIEVER
                ))
                .progress())
        .then(progress -> {
          var arn = Utils.buildRetrieverArn(request, progress.getResourceModel());
          return TagUtils.updateTags(ResourceModel.TYPE_NAME, progress, request, arn, proxyClient, logger);
        })
        .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
  }

  private UpdateRetrieverResponse callUpdateRetriever(UpdateRetrieverRequest request, ProxyClient<QBusinessClient> proxyClient) {
    return proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::updateRetriever);
  }
}
