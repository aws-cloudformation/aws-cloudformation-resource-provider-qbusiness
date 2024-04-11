package software.amazon.qbusiness.plugin;

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
import software.amazon.awssdk.services.qbusiness.model.DeletePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.DeletePluginResponse;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.PluginBuildStatus;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class DeleteHandlerTest extends AbstractTestBase {

    private static final String APPLICATION_ID = "ApplicationId";
    private static final String PLUGIN_ID = "PluginId";
    private static final String CLIENT_TOKEN = "ClientToken";
    private static final String AWS_PARTITION = "aws";
    private static final String ACCOUNT_ID = "123456789012";
    private static final String REGION = "us-west-2";

    private AmazonWebServicesClientProxy proxy;

    private ProxyClient<QBusinessClient> proxyClient;

    @Mock
    private QBusinessClient qBusinessClient;

    private AutoCloseable testMocks;
    private DeleteHandler underTest;
    private ResourceModel resourceModel;
    private ResourceHandlerRequest<ResourceModel> request;


    @BeforeEach
    public void setup() {
        testMocks = MockitoAnnotations.openMocks(this);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        this.underTest = new DeleteHandler();

        resourceModel = ResourceModel.builder()
                    .applicationId(APPLICATION_ID)
                    .pluginId(PLUGIN_ID)
                .build();

        request = ResourceHandlerRequest.<ResourceModel>builder()
                    .awsPartition(AWS_PARTITION)
                    .region(REGION)
                    .awsAccountId(ACCOUNT_ID)
                    .desiredResourceState(resourceModel)
                    .clientRequestToken(CLIENT_TOKEN)
                .build();
    }

    @AfterEach
    public void tear_down() throws Exception {
        verify(qBusinessClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(qBusinessClient);
        testMocks.close();
    }

    @Test
    public void handleRequest_SimpleSuccess() {

        when(proxyClient.client().deletePlugin(any(DeletePluginRequest.class)))
                .thenReturn(DeletePluginResponse.builder().build());
        when(proxyClient.client().getPlugin(any(GetPluginRequest.class))).thenThrow(ResourceNotFoundException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(qBusinessClient).deletePlugin(any(DeletePluginRequest.class));
        verify(qBusinessClient).getPlugin(any(GetPluginRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        verify(qBusinessClient).deletePlugin(
        argThat((ArgumentMatcher<DeletePluginRequest>) t -> t.applicationId().equals(APPLICATION_ID)));

        verify(qBusinessClient).deletePlugin(
        argThat((ArgumentMatcher<DeletePluginRequest>) t -> t.pluginId().equals(PLUGIN_ID)));

    }

    @Test
    public void handleRequest_StabilizeFromDeleteInProgressToDeleted() {

        when(proxyClient.client().deletePlugin(any(DeletePluginRequest.class)))
                .thenReturn(DeletePluginResponse.builder().build());
        when(proxyClient.client().getPlugin(any(GetPluginRequest.class)))
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .buildStatus(PluginBuildStatus.DELETE_IN_PROGRESS)
                        .build())
                .thenThrow(ResourceNotFoundException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(qBusinessClient).deletePlugin(any(DeletePluginRequest.class));
        verify(qBusinessClient, times(2)).getPlugin(any(GetPluginRequest.class));
    }

    @Test
    public void handleRequest_ThrowsExpectedErrorWhenStabilizationFails() {

        when(proxyClient.client().deletePlugin(any(DeletePluginRequest.class)))
                .thenReturn(DeletePluginResponse.builder().build());
        when(proxyClient.client().getPlugin(any(GetPluginRequest.class)))
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .buildStatus(PluginBuildStatus.DELETE_FAILED)
                        .build());

        assertThatThrownBy(() -> underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnNotStabilizedException.class);;

        verify(qBusinessClient).deletePlugin(any(DeletePluginRequest.class));
        verify(qBusinessClient, times(1)).getPlugin(any(GetPluginRequest.class));
    }

    private static Stream<Arguments> serviceErrorAndHandlerCodes() {
    return Stream.of(
            Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
            Arguments.of(ConflictException.builder().build(), HandlerErrorCode.ResourceConflict),
            Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
            Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
            Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied),
            Arguments.of(InternalServerException.builder().build(), HandlerErrorCode.GeneralServiceException)
        );
    }

    @ParameterizedTest
    @MethodSource("serviceErrorAndHandlerCodes")
    public void testThatItReturnsExpectedHandlerErrorCodeForServiceError(QBusinessException serviceError, HandlerErrorCode expectedErrorCode) {

      // set up test
      when(qBusinessClient.deletePlugin(any(DeletePluginRequest.class))).thenThrow(serviceError);

      // call method under test
      final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
          proxy, request, new CallbackContext(), proxyClient, logger
      );

      // verify
      assertThat(responseProgress).isNotNull();
      assertThat(responseProgress.isSuccess()).isFalse();
      assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
      verify(qBusinessClient).deletePlugin(any(DeletePluginRequest.class));
      assertThat(responseProgress.getErrorCode()).isEqualTo(expectedErrorCode);
      assertThat(responseProgress.getResourceModels()).isNull();

    }

}
