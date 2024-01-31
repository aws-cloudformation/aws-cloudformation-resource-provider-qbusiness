package software.amazon.qbusiness.retriever;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.KendraIndexConfiguration;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.RetrieverConfiguration;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ReadHandlerTest extends AbstractTestBase {
  private static final String APP_ID = "ApplicationId";
  private static final String RETRIEVER_ID = "RetrieverId";
  private static final String RETRIEVER_NAME = "RetrieverName";
  private static final String RETRIEVER_TYPE = "KENDRA_INDEX";
  private static final String RETRIEVER_STATUS = "ACTIVE";
  private static final String INDEX_ID = "IndexId";
  private static final String ROLE_ARN = "role-1";
  private static final Long CREATED_TIME = 1697824935000L;
  private static final Long UPDATED_TIME = 1697839335000L;
  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testMocks;

  private ReadHandler underTest;
  private ResourceModel model;
  private ResourceHandlerRequest<ResourceModel> request;
  private RetrieverConfiguration retrieverConfiguration;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);
    this.underTest = new ReadHandler();

    KendraIndexConfiguration kendraIndexConfiguration = KendraIndexConfiguration.builder()
        .indexId(INDEX_ID)
        .build();
    retrieverConfiguration = RetrieverConfiguration.builder()
        .kendraIndexConfiguration(kendraIndexConfiguration)
        .build();

    model = ResourceModel.builder()
        .applicationId(APP_ID)
        .retrieverId(RETRIEVER_ID)
        .build();
    request = ResourceHandlerRequest.<ResourceModel>builder()
        .awsPartition("aws")
        .region("us-west-2")
        .awsAccountId("123412341234")
        .desiredResourceState(model)
        .build();
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);
    testMocks.close();
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    when(proxyClient.client().getRetriever(any(GetRetrieverRequest.class)))
        .thenReturn(GetRetrieverResponse.builder()
            .applicationId(APP_ID)
            .retrieverId(RETRIEVER_ID)
            .displayName(RETRIEVER_NAME)
            .type(RETRIEVER_TYPE)
            .status(RETRIEVER_STATUS)
            .configuration(retrieverConfiguration)
            .roleArn(ROLE_ARN)
            .createdAt(Instant.ofEpochMilli(CREATED_TIME))
            .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
            .build());
    when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenReturn(ListTagsForResourceResponse.builder()
            .tags(List.of(Tag.builder()
                .key("Tag 1")
                .value("Tag 2")
                .build()))
            .build());

    final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

    verify(sdkClient).getRetriever(any(GetRetrieverRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();

    ResourceModel resultModel = response.getResourceModel();

    assertThat(resultModel.getApplicationId()).isEqualTo(APP_ID);
    assertThat(resultModel.getRetrieverId()).isEqualTo(RETRIEVER_ID);
    assertThat(resultModel.getType()).isEqualTo(RETRIEVER_TYPE);
    assertThat(resultModel.getStatus()).isEqualTo(RETRIEVER_STATUS);
    assertThat(resultModel.getDisplayName()).isEqualTo(RETRIEVER_NAME);
    assertThat(resultModel.getConfiguration()).isEqualTo(Translator.fromServiceRetrieverConfiguration(retrieverConfiguration));
    assertThat(resultModel.getRoleArn()).isEqualTo(ROLE_ARN);
    assertThat(resultModel.getCreatedAt()).isEqualTo(Instant.ofEpochMilli(CREATED_TIME).toString());
    assertThat(resultModel.getUpdatedAt()).isEqualTo(Instant.ofEpochMilli(UPDATED_TIME).toString());

    List<Map.Entry<String, String>> tags = resultModel.getTags().stream().map(tag -> Map.entry(tag.getKey(), tag.getValue())).toList();
    assertThat(tags).isEqualTo(List.of(
        Map.entry("Tag 1", "Tag 2")
    ));
  }

  @Test
  public void handleRequest_withMissingProperties() {
    when(proxyClient.client().getRetriever(any(GetRetrieverRequest.class)))
        .thenReturn(GetRetrieverResponse.builder()
            .applicationId(APP_ID)
            .retrieverId(RETRIEVER_ID)
            .displayName(RETRIEVER_NAME)
            .type(RETRIEVER_TYPE)
            .status(RETRIEVER_STATUS)
            .configuration(retrieverConfiguration)
            .roleArn(ROLE_ARN)
            .createdAt(Instant.ofEpochMilli(CREATED_TIME))
            .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
            .build());

    final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

    verify(sdkClient).getRetriever(any(GetRetrieverRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
    assertThat(response.getResourceModels()).isNull();
    assertThat(response.getMessage()).isNull();
    assertThat(response.getErrorCode()).isNull();

    ResourceModel resultModel = response.getResourceModel();

    assertThat(resultModel.getApplicationId()).isEqualTo(APP_ID);
    assertThat(resultModel.getRetrieverId()).isEqualTo(RETRIEVER_ID);
    assertThat(resultModel.getType()).isEqualTo(RETRIEVER_TYPE);
    assertThat(resultModel.getStatus()).isEqualTo(RETRIEVER_STATUS);
    assertThat(resultModel.getDisplayName()).isEqualTo(RETRIEVER_NAME);
    assertThat(resultModel.getConfiguration()).isEqualTo(Translator.fromServiceRetrieverConfiguration(retrieverConfiguration));
    assertThat(resultModel.getRoleArn()).isEqualTo(ROLE_ARN);
    assertThat(resultModel.getCreatedAt()).isEqualTo(Instant.ofEpochMilli(CREATED_TIME).toString());
    assertThat(resultModel.getUpdatedAt()).isEqualTo(Instant.ofEpochMilli(UPDATED_TIME).toString());
  }

  private static Stream<Arguments> serviceErrorAndExpectedCfnCode() {
    return Stream.of(
        Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied),
        Arguments.of(InternalServerException.builder().build(), HandlerErrorCode.GeneralServiceException)
    );
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCode(QBusinessException serviceError, HandlerErrorCode cfnErrorCode) {
    when(proxyClient.client().getRetriever(any(GetRetrieverRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getRetriever(any(GetRetrieverRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCodeWhenListTagsForResourceFails(QBusinessException serviceError, HandlerErrorCode cfnErrorCode) {
    // set up test scenario
    when(proxyClient.client().getRetriever(any(GetRetrieverRequest.class)))
        .thenReturn(GetRetrieverResponse.builder()
            .applicationId(APP_ID)
            .retrieverId(RETRIEVER_ID)
            .displayName(RETRIEVER_NAME)
            .type(RETRIEVER_TYPE)
            .status(RETRIEVER_STATUS)
            .configuration(retrieverConfiguration)
            .roleArn(ROLE_ARN)
            .createdAt(Instant.ofEpochMilli(CREATED_TIME))
            .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
            .build());

    when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getRetriever(any(GetRetrieverRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }
}
