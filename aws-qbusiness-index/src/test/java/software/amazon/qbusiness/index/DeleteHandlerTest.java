package software.amazon.qbusiness.index;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.DeleteIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.IndexStatus;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.stream.Stream;

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

public class DeleteHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "a197dafc-2158-4f93-ab0d-b1c361c39838";
  private static final String INDEX_ID = "44444444-2158-4f93-ab0d-b1c361c39838";

  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private DeleteHandler underTest;

  private AutoCloseable testMocks;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel toDeleteModel;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);

    var testBackOff = Constant.of()
        .delay(Duration.ofSeconds(2))
        .timeout(Duration.ofSeconds(45))
        .build();
    underTest = new DeleteHandler(testBackOff);

    toDeleteModel = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .build();

    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(toDeleteModel)
        .awsAccountId("123456")
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
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
    // set up test
    when(sdkClient.deleteIndex(any(DeleteIndexRequest.class))).thenReturn(DeleteIndexResponse.builder().build());
    when(sdkClient.getIndex(any(GetIndexRequest.class))).thenThrow(ResourceNotFoundException.builder().build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    assertThat(resultProgress.getResourceModel()).isNull();
    assertThat(resultProgress.getResourceModels()).isNull();
    assertThat(resultProgress.getErrorCode()).isNull();

    verify(sdkClient).deleteIndex(
        argThat((ArgumentMatcher<DeleteIndexRequest>) t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID))
    );
    verify(sdkClient).getIndex(
        argThat((ArgumentMatcher<GetIndexRequest>) t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID))
    );
  }

  @Test
  public void handleMovingFromDeletingToNotFound() {
    // set up test
    when(sdkClient.deleteIndex(any(DeleteIndexRequest.class))).thenReturn(DeleteIndexResponse.builder().build());
    when(sdkClient.getIndex(any(GetIndexRequest.class)))
        .thenReturn(
            GetIndexResponse.builder()
                .applicationId(APP_ID)
                .indexId(INDEX_ID)
                .status(IndexStatus.ACTIVE)
                .build()
        )
        .thenReturn(
            GetIndexResponse.builder()
                .applicationId(APP_ID)
                .indexId(INDEX_ID)
                .status(IndexStatus.DELETING)
                .build()
        )
        .thenThrow(ResourceNotFoundException.builder().build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    assertThat(resultProgress.getResourceModel()).isNull();
    assertThat(resultProgress.getResourceModels()).isNull();
    assertThat(resultProgress.getErrorCode()).isNull();

    verify(sdkClient).deleteIndex(
        argThat((ArgumentMatcher<DeleteIndexRequest>) t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID))
    );

    verify(sdkClient, times(3)).getIndex(
        argThat((ArgumentMatcher<GetIndexRequest>) t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID))
    );
  }

  private static Stream<Arguments> serviceErrorAndHandlerCodes() {
    return Stream.of(
        Arguments.of(ConflictException.builder().build(), HandlerErrorCode.ResourceConflict),
        Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied)
    );
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndHandlerCodes")
  public void testThatItReturnsExpectedHandlerErrorCodeForServiceError(QBusinessException serviceError, HandlerErrorCode expectedErrorCode) {
    // set up test
    when(sdkClient.deleteIndex(any(DeleteIndexRequest.class))).thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.isSuccess()).isFalse();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).deleteIndex(any(DeleteIndexRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(expectedErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }

  @Test
  public void testItThrowsUnexpectedErrorWhenDeleteCallFails() {
    // set up
    when(sdkClient.deleteIndex(any(DeleteIndexRequest.class)))
        .thenThrow(InternalServerException.builder().build());

    // call and verify
    assertThatThrownBy(() -> underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    )).isInstanceOf(InternalServerException.class);

    verify(sdkClient).deleteIndex(any(DeleteIndexRequest.class));
  }
}
