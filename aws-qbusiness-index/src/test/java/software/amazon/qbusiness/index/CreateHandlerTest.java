package software.amazon.qbusiness.index;

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
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.AttributeType;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.CreateIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.ErrorDetail;
import software.amazon.awssdk.services.qbusiness.model.IndexType;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.IndexStatus;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.Status;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "a197dafc-2158-4f93-ab0d-b1c361c39838";
  private static final String INDEX_ID = "22222222-1596-4f1a-a3c8-e5f4b33d9fe5";

  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  QBusinessClient QBusinessClient;

  private Constant testBackOff;
  private CreateHandler underTest;

  private AutoCloseable testMocks;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel createModel;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    QBusinessClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, QBusinessClient);

    testBackOff = Constant.of()
        .delay(Duration.ofSeconds(5))
        .timeout(Duration.ofSeconds(45))
        .build();

    underTest = new CreateHandler(testBackOff);

    createModel = ResourceModel.builder()
        .displayName("TheMeta")
        .description("A Description")
        .applicationId(APP_ID)
        .capacityConfiguration(new IndexCapacityConfiguration(10D))
        .type(IndexType.ENTERPRISE.toString())
        .build();

    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(createModel)
        .awsAccountId("123456")
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
        .build();
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(QBusinessClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(QBusinessClient);
    testMocks.close();
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    // set up scenario
    when(QBusinessClient.createIndex(any(CreateIndexRequest.class)))
        .thenReturn(CreateIndexResponse.builder()
            .indexId(INDEX_ID)
            .build()
        );
    when(QBusinessClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build());
    when(QBusinessClient.getIndex(any(GetIndexRequest.class)))
        .thenReturn(GetIndexResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .status(IndexStatus.ACTIVE)
            .type(IndexType.ENTERPRISE)
            .description(createModel.getDescription())
            .displayName(createModel.getDisplayName())
            .capacityConfiguration(software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
                .units(10)
                .build())
            .build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    var model = resultProgress.getResourceModel();
    assertThat(model.getDisplayName()).isEqualTo(createModel.getDisplayName());
    assertThat(model.getApplicationId()).isEqualTo(createModel.getApplicationId());
    assertThat(model.getIndexId()).isEqualTo(createModel.getIndexId());
    assertThat(model.getDescription()).isEqualTo(createModel.getDescription());
    assertThat(model.getType()).isEqualTo(createModel.getType());
    assertThat(model.getStatus()).isEqualTo(IndexStatus.ACTIVE.toString());
    assertThat(model.getCapacityConfiguration().getUnits()).isEqualTo(createModel.getCapacityConfiguration().getUnits());

    verify(QBusinessClient).createIndex(any(CreateIndexRequest.class));
    verify(QBusinessClient, times(2)).getIndex(
        argThat((ArgumentMatcher<GetIndexRequest>) t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID))
    );
    verify(QBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void handleCreateRequestWithDocumentAttributeConfiguration() {
    // set up scenario
    when(QBusinessClient.createIndex(any(CreateIndexRequest.class)))
        .thenReturn(CreateIndexResponse.builder()
            .indexId(INDEX_ID)
            .build()
        );
    when(QBusinessClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build());

    var statusResponseBuilder = GetIndexResponse.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .type(IndexType.ENTERPRISE)
        .createdAt(Instant.ofEpochMilli(1697824935000L))
        .updatedAt(Instant.ofEpochMilli(1697839335000L))
        .status(IndexStatus.ACTIVE);

    when(QBusinessClient.getIndex(any(GetIndexRequest.class)))
        .thenReturn(statusResponseBuilder.status(IndexStatus.ACTIVE).build())
        .thenReturn(statusResponseBuilder.status(IndexStatus.UPDATING).build())
        .thenReturn(statusResponseBuilder.status(IndexStatus.ACTIVE).build())
        .thenReturn(statusResponseBuilder
            .status(IndexStatus.ACTIVE)
            .type(IndexType.ENTERPRISE)
            .description(createModel.getDescription())
            .displayName(createModel.getDisplayName())
            .capacityConfiguration(software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
                .units(10)
                .build())
            .build());
    createModel.setDocumentAttributeConfigurations(List.of(
        DocumentAttributeConfiguration.builder()
            .name("that-attrib")
            .type(AttributeType.STRING.toString())
            .search(Status.DISABLED.toString())
            .build()
    ));

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(QBusinessClient).createIndex(any(CreateIndexRequest.class));
    verify(QBusinessClient, times(2)).getIndex(any(GetIndexRequest.class));
    verify(QBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void handleRequestFromProcessingStateToActive() {
    // set up scenario
    var getResponse = GetIndexResponse.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .type(IndexType.ENTERPRISE)
        .createdAt(Instant.ofEpochMilli(1697824935000L))
        .updatedAt(Instant.ofEpochMilli(1697839335000L))
        .description(createModel.getDescription())
        .displayName(createModel.getDisplayName())
        .capacityConfiguration(software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
            .units(10)
            .build())
        .build();

    when(QBusinessClient.createIndex(any(CreateIndexRequest.class)))
        .thenReturn(CreateIndexResponse.builder()
            .indexId(INDEX_ID)
            .build()
        );
    when(QBusinessClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build());

    when(QBusinessClient.getIndex(any(GetIndexRequest.class)))
        .thenReturn(
            getResponse.toBuilder().status(IndexStatus.CREATING).build(),
            getResponse.toBuilder().status(IndexStatus.ACTIVE).build()
        );

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(QBusinessClient).createIndex(any(CreateIndexRequest.class));
    verify(QBusinessClient, times(3)).getIndex(
        argThat((ArgumentMatcher<GetIndexRequest>) t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID))
    );
    verify(QBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void testItFailsWithErrorMessageWhenGetReturnsFailStatus() {
    // set up
    when(QBusinessClient.createIndex(any(CreateIndexRequest.class)))
        .thenReturn(CreateIndexResponse.builder()
            .indexId(INDEX_ID)
            .build()
        );
    when(QBusinessClient.getIndex(any(GetIndexRequest.class)))
        .thenReturn(GetIndexResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .type(IndexType.ENTERPRISE)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .status(IndexStatus.FAILED)
            .description(createModel.getDescription())
            .error(ErrorDetail.builder().errorMessage("There was a problem in get index.").build())
            .displayName(createModel.getDisplayName())
            .capacityConfiguration(software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
                .units(10)
                .build())
            .build());

    // call method under test & verify
    assertThatThrownBy(() -> underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    )).isInstanceOf(CfnNotStabilizedException.class);

    verify(QBusinessClient).createIndex(any(CreateIndexRequest.class));
    verify(QBusinessClient).getIndex(any(GetIndexRequest.class));
  }

  private static Stream<Arguments> createIndexErrorsAndExpectedCodes() {
    return Stream.of(
        Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ConflictException.builder().build(), HandlerErrorCode.ResourceConflict),
        Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
        Arguments.of(ServiceQuotaExceededException.builder().build(), HandlerErrorCode.ServiceLimitExceeded),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied),
        Arguments.of(InternalServerException.builder().build(), HandlerErrorCode.GeneralServiceException)
    );
  }

  @ParameterizedTest
  @MethodSource("createIndexErrorsAndExpectedCodes")
  public void testItReturnsExpectedCfnErrorWhenCreateIndexFails(
      final QBusinessException serviceError,
      final HandlerErrorCode expectedHandlerErrorCode) {
    // set up
    when(QBusinessClient.createIndex(any(CreateIndexRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(QBusinessClient).createIndex(any(CreateIndexRequest.class));
    assertThat(resultProgress.getErrorCode()).isEqualTo(expectedHandlerErrorCode);
  }
}
