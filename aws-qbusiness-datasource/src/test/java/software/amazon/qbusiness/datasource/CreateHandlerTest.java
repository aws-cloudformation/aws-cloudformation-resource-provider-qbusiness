package software.amazon.qbusiness.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.DocumentEnrichmentConditionOperator;
import software.amazon.awssdk.services.qbusiness.model.ErrorDetail;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class CreateHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "3f7bf9f2-205f-47af-8e35-804fc749fbf8";
  private static final String INDEX_ID = "31b1150f-4988-4d79-8952-8cac97d54322";
  private static final String DATA_SOURCE_ID = "b1d8bb25-bd6f-4bfd-8286-475b029b52a8";

  private AmazonWebServicesClientProxy proxy;

  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testMocks;

  private CreateHandler underTest;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel model;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);

    underTest = new CreateHandler(Constant.of()
        .timeout(Duration.ofSeconds(60))
        .delay(Duration.ofSeconds(3))
        .build());

    model = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
        .displayName("Name Name")
        .description("We are groot")
        .roleArn("roleyroley")
        .syncSchedule("0 11 * * 4")
        .vpcConfiguration(DataSourceVpcConfiguration.builder()
            .securityGroupIds(List.of("secur1", "secure2"))
            .subnetIds(List.of("sub1", "sub2"))
            .build())
        .configuration(
            Map.of(
                    "Type", "WebcrawlerV2",
                    "Links", List.of("link1", "link2"),
                    "depth", 50,
                    "overrides", Map.of(
                        "a", 10
                    )
                )
        )
        .documentEnrichmentConfiguration(DocumentEnrichmentConfiguration.builder()
            .preExtractionHookConfiguration(HookConfiguration.builder()
                .roleArn("extractrole")
                .lambdaArn("lambda")
                .s3BucketName("bucket")
                .invocationCondition(DocumentAttributeCondition.builder()
                    .operator(DocumentEnrichmentConditionOperator.EQUALS.toString())
                    .value(DocumentAttributeValue.builder().stringValue("wow").build())
                    .key("datkey")
                    .build()
                )
                .build())
            .build())
        .build();

    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .awsPartition("aws")
        .region("us-west-2")
        .awsAccountId("111122223333")
        .desiredResourceState(model)
        .build();

    when(sdkClient.createDataSource(any(CreateDataSourceRequest.class)))
        .thenReturn(CreateDataSourceResponse.builder()
            .dataSourceId(DATA_SOURCE_ID)
            .build()
        );
    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build());
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);

    testMocks.close();
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    // set up
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenReturn(GetDataSourceResponse.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
        .status(DataSourceStatus.ACTIVE)
        .build());

    // call method
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();

    var createReqCaptor = ArgumentCaptor.forClass(CreateDataSourceRequest.class);
    verify(sdkClient).createDataSource(createReqCaptor.capture());
    verify(sdkClient, times(2)).getDataSource(argThat(
        (ArgumentMatcher<GetDataSourceRequest>) t -> t.dataSourceId().equals(DATA_SOURCE_ID)
    ));
    verify(sdkClient).listTagsForResource(argThat(
        (ArgumentMatcher<ListTagsForResourceRequest>) t -> t.resourceARN().contains(DATA_SOURCE_ID)
    ));

    CreateDataSourceRequest argCreateReq = createReqCaptor.getValue();
    Document template = argCreateReq.configuration();

    assertThat(template).isEqualTo(Document.fromMap(Map.of(
        "Type", Document.fromString("WebcrawlerV2"),
        "Links", Document.fromList(List.of(Document.fromString("link1"), Document.fromString("link2"))),
        "depth", Document.fromNumber(50),
        "overrides", Document.fromMap(Map.of(
            "a", Document.fromNumber(10)
        ))
    )));
  }

  @Test
  public void handleRequest_StabilizeFomCreatingToActive() {
    // set up
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class)))
        .thenReturn(
            GetDataSourceResponse.builder()
                .applicationId(APP_ID)
                .indexId(INDEX_ID)
                .dataSourceId(DATA_SOURCE_ID)
                .status(DataSourceStatus.CREATING)
                .build()
        )
        .thenReturn(GetDataSourceResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .dataSourceId(DATA_SOURCE_ID)
            .status(DataSourceStatus.ACTIVE)
            .build()
        );

    // call method
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();

    verify(sdkClient).createDataSource(any(CreateDataSourceRequest.class));
    verify(sdkClient, times(3)).getDataSource(any(GetDataSourceRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void testItThrowsExpectedErrorWhenStabilizationFails() {
    // set up
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class)))
        .thenReturn(
            GetDataSourceResponse.builder()
                .applicationId(APP_ID)
                .indexId(INDEX_ID)
                .dataSourceId(DATA_SOURCE_ID)
                .status(DataSourceStatus.FAILED)
                .error(ErrorDetail.builder().errorMessage("Such error, very fail").build())
                .build()
        );

    // call and verify
    assertThatThrownBy(() -> underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    )).isInstanceOf(CfnNotStabilizedException.class);

    verify(sdkClient).createDataSource(any(CreateDataSourceRequest.class));
    verify(sdkClient, times(1)).getDataSource(any(GetDataSourceRequest.class));
  }

  private static Stream<Arguments> serviceErrorsAndHandlerCodes() {
    return Stream.of(
        Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
        Arguments.of(ConflictException.builder().build(), HandlerErrorCode.ResourceConflict),
        Arguments.of(ServiceQuotaExceededException.builder().build(), HandlerErrorCode.ServiceLimitExceeded),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied),
        Arguments.of(InternalServerException.builder().build(), HandlerErrorCode.GeneralServiceException)
    );
  }

  @ParameterizedTest
  @MethodSource("serviceErrorsAndHandlerCodes")
  public void testThatItReturnsExpectedCfnHandlerCodeWhenCreateCallFails(QBusinessException serviceError, HandlerErrorCode expectedErrorCode) {
    when(sdkClient.createDataSource(any(CreateDataSourceRequest.class))).thenThrow(serviceError);

    // under test call
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(resultProgress.getErrorCode()).isEqualTo(expectedErrorCode);
    verify(sdkClient).createDataSource(any(CreateDataSourceRequest.class));
  }
}
