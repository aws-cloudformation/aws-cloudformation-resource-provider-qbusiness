package software.amazon.qbusiness.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.stream.Stream;

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
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class DeleteHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "5d31a0e5-2d19-4ac3-90da-34534fa1d2df";
  private static final String INDEX_ID = "9a2515e0-5760-4414-9fe2-c17e95406e5f";
  private static final String DATA_SOURCE_ID = "5f173c8b-16c2-4e4f-bc3d-46b9cfc424a4";

  private AmazonWebServicesClientProxy proxy;

  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testMocks;
  private DeleteHandler underTest;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel toDeleteModel;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    proxyClient = MOCK_PROXY(proxy, sdkClient);

    underTest = new DeleteHandler(Constant.of()
        .timeout(Duration.ofSeconds(60))
        .delay(Duration.ofSeconds(2))
        .build());

    toDeleteModel = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
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
    when(sdkClient.deleteDataSource(any(DeleteDataSourceRequest.class))).thenReturn(DeleteDataSourceResponse.builder().build());
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenThrow(ResourceNotFoundException.builder().build());

    // call the method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    assertThat(resultProgress.getResourceModel()).isNull();
    assertThat(resultProgress.getResourceModels()).isNull();
    assertThat(resultProgress.getErrorCode()).isNull();

    verify(sdkClient).deleteDataSource(argThat(
        (ArgumentMatcher<DeleteDataSourceRequest>) t -> t.dataSourceId().equals(DATA_SOURCE_ID)
    ));

    verify(sdkClient).getDataSource(argThat(
        (ArgumentMatcher<GetDataSourceRequest>) t -> t.dataSourceId().equals(DATA_SOURCE_ID)
    ));
  }

  @Test
  public void testThatItStabilizesAfterDeletingStatus() {
    // set up test
    when(sdkClient.deleteDataSource(any(DeleteDataSourceRequest.class))).thenReturn(DeleteDataSourceResponse.builder().build());
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class)))
        .thenReturn(
            GetDataSourceResponse.builder()
                .applicationId(APP_ID)
                .indexId(INDEX_ID)
                .dataSourceId(DATA_SOURCE_ID)
                .status(DataSourceStatus.DELETING)
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
    verify(sdkClient).deleteDataSource(argThat(
        (ArgumentMatcher<DeleteDataSourceRequest>) t -> t.dataSourceId().equals(DATA_SOURCE_ID)
    ));

    verify(sdkClient, times(2)).getDataSource(argThat(
        (ArgumentMatcher<GetDataSourceRequest>) t -> t.dataSourceId().equals(DATA_SOURCE_ID)
    ));
  }

  private static Stream<Arguments> stabilizeServiceErrors() {
    return Stream.of(
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied),
        Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ConflictException.builder().build(), HandlerErrorCode.ResourceConflict),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling)
    );
  }

  private static Stream<Arguments> serviceErrorsAndHandlerCodes() {
    return Stream.concat(
        stabilizeServiceErrors(),
        Stream.of(Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound))
    );
  }

  @ParameterizedTest
  @MethodSource("serviceErrorsAndHandlerCodes")
  public void testThatItReturnsExpectedCfnErrorCodeForCreateFailures(
      QBusinessException serviceError,
      HandlerErrorCode expectedCfnErrorCode
  ) {
    // set up
    when(sdkClient.deleteDataSource(any(DeleteDataSourceRequest.class))).thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.isSuccess()).isFalse();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).deleteDataSource(any(DeleteDataSourceRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(expectedCfnErrorCode);
  }

  @ParameterizedTest
  @MethodSource("stabilizeServiceErrors")
  public void testThatItThrowsWhenAnErrorOtherThanNotFoundIsRaisedDuringStabilizeCheck(
      QBusinessException serviceError,
      HandlerErrorCode expectedCfnErrorCode
  ) {
    // set up
    when(sdkClient.deleteDataSource(any(DeleteDataSourceRequest.class))).thenReturn(DeleteDataSourceResponse.builder().build());
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenThrow(serviceError);

    // call method under test & verify
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.isSuccess()).isFalse();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(responseProgress.getErrorCode()).isEqualTo(expectedCfnErrorCode);

    verify(sdkClient).deleteDataSource(any(DeleteDataSourceRequest.class));
    verify(sdkClient).getDataSource(any(GetDataSourceRequest.class));
  }
}
