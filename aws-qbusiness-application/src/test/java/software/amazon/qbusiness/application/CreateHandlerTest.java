package software.amazon.qbusiness.application;

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

import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.ApplicationStatus;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.CreateApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateApplicationResponse;
import software.amazon.awssdk.services.qbusiness.model.ErrorDetail;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationResponse;
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

  private static final String APP_ID = "a197dafc-2158-4f93-ab0d-b1c361c39838";

  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  QBusinessClient sdkClient;

  private Constant testBackOff;
  private CreateHandler underTest;

  private AutoCloseable testMocks;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel createModel;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);

    testBackOff = Constant.of()
        .delay(Duration.ofSeconds(5))
        .timeout(Duration.ofSeconds(45))
        .build();

    underTest = new CreateHandler(testBackOff);

    createModel = ResourceModel.builder()
        .displayName("TheMeta")
        .description("A Description")
        .roleArn("such role, very arn")
        .build();

    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(createModel)
        .awsAccountId("123456")
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
        .build();

    when(sdkClient.createApplication(any(CreateApplicationRequest.class)))
        .thenReturn(CreateApplicationResponse.builder()
            .applicationId(APP_ID)
            .build()
        );
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);
    testMocks.close();
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    // set up scenario
    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build());
    when(sdkClient.getApplication(any(GetApplicationRequest.class)))
        .thenReturn(GetApplicationResponse.builder()
            .applicationId(APP_ID)
            .status(ApplicationStatus.ACTIVE)
            .description(createModel.getDescription())
            .displayName(createModel.getDisplayName())
            .roleArn(createModel.getRoleArn())
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
    assertThat(model.getRoleArn()).isEqualTo(createModel.getRoleArn());
    assertThat(model.getDescription()).isEqualTo(createModel.getDescription());
    assertThat(model.getStatus()).isEqualTo(ApplicationStatus.ACTIVE.toString());

    verify(sdkClient).createApplication(any(CreateApplicationRequest.class));
    verify(sdkClient, times(2)).getApplication(
        argThat((ArgumentMatcher<GetApplicationRequest>) t -> t.applicationId().equals(APP_ID))
    );
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void handleRequestFromProcessingStateToActive() {
    // set up scenario
    var getResponse = GetApplicationResponse.builder()
        .applicationId(APP_ID)
        .description(createModel.getDescription())
        .displayName(createModel.getDisplayName())
        .roleArn(createModel.getRoleArn())
        .build();

    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build());

    when(sdkClient.getApplication(any(GetApplicationRequest.class)))
        .thenReturn(
            getResponse.toBuilder().status(ApplicationStatus.CREATING).build(),
            getResponse.toBuilder().status(ApplicationStatus.ACTIVE).build()
        );

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(sdkClient).createApplication(any(CreateApplicationRequest.class));
    verify(sdkClient, times(3)).getApplication(
        argThat((ArgumentMatcher<GetApplicationRequest>) t -> t.applicationId().equals(APP_ID))
    );
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void testItFailsWithErrorMessageWhenGetReturnsFailStatus() {
    // set up
    when(sdkClient.getApplication(any(GetApplicationRequest.class)))
        .thenReturn(GetApplicationResponse.builder()
            .applicationId(APP_ID)
            .status(ApplicationStatus.FAILED)
            .error(ErrorDetail.builder().errorMessage("There was like, a problem.").build())
            .build()
        );

    // call method under test & verify

    // verify
    assertThatThrownBy(() -> underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    )).isInstanceOf(CfnNotStabilizedException.class);

    verify(sdkClient).createApplication(any(CreateApplicationRequest.class));
    verify(sdkClient, times(1)).getApplication(any(GetApplicationRequest.class));
  }

  private static Stream<Arguments> createApplicationErrorsAndExpectedCodes() {
    return Stream.of(
        Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ConflictException.builder().build(), HandlerErrorCode.ResourceConflict),
        Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
        Arguments.of(ServiceQuotaExceededException.builder().build(), HandlerErrorCode.ServiceLimitExceeded),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied)
    );
  }

  @ParameterizedTest
  @MethodSource("createApplicationErrorsAndExpectedCodes")
  public void testItReturnsExpectedCfnErrorWhenCreateApplicationFails(
      QBusinessException serviceError,
      HandlerErrorCode expectedHandlerErrorCode
  ) {
    // set up
    when(sdkClient.createApplication(any(CreateApplicationRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).createApplication(any(CreateApplicationRequest.class));
    assertThat(resultProgress.getErrorCode()).isEqualTo(expectedHandlerErrorCode);
  }

  @Test
  public void testItThrowsUnexpectedErrorWhenCreateCallFails() {
    // set up
    when(QBusinessClient.createApplication(any(CreateApplicationRequest.class)))
        .thenThrow(InternalServerException.builder().build());

    // call and verify
    assertThatThrownBy(() -> underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    )).isInstanceOf(InternalServerException.class);

    verify(QBusinessClient).createApplication(any(CreateApplicationRequest.class));
  }
}
