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
import java.time.Instant;
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

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.CreatePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.CreatePluginResponse;
import software.amazon.awssdk.services.qbusiness.model.PluginBuildStatus;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginResponse;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class CreateHandlerTest extends AbstractTestBase {

    private static final String APPLICATION_ID = "ApplicationId";
    private static final String PLUGIN_ID = "PluginId";
    private static final String PLUGIN_NAME = "PluginName";
    private static final String PLUGIN_TYPE = "JIRA";
    private static final String PLUGIN_STATE = "ACTIVE";
    private static final String SERVER_URL = "ServerUrl";
    private static final String ROLE_ARN = "role-1";
    private static final String SECRET_ARN = "secret-1";
    private static final String CLIENT_TOKEN = "client-token";
    private static final Long CREATED_TIME = 1697824935000L;
    private static final Long UPDATED_TIME = 1697839335000L;
    private static final String AWS_PARTITION = "aws";
    private static final String ACCOUNT_ID = "123456789012";
    private static final String REGION = "us-west-2";

    private AmazonWebServicesClientProxy proxy;

    private ProxyClient<QBusinessClient> proxyClient;

    @Mock
    QBusinessClient qBusinessClient;

    private AutoCloseable testMocks;
    private CreateHandler underTest;
    private PluginAuthConfiguration serviceAuthConfiguration;
    private software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration cfnAuthConfiguration;
    private ResourceModel model;
    private ResourceHandlerRequest<ResourceModel> request;

    @BeforeEach
    public void setup() {
        testMocks = MockitoAnnotations.openMocks(this);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        this.underTest = new CreateHandler();

        serviceAuthConfiguration = PluginAuthConfiguration.builder()
                .basicAuthConfiguration(BasicAuthConfiguration.builder()
                        .roleArn(ROLE_ARN)
                        .secretArn(SECRET_ARN)
                        .build())
                .build();

        cfnAuthConfiguration = software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration.builder()
                .basicAuthConfiguration(software.amazon.awssdk.services.qbusiness.model.BasicAuthConfiguration.builder()
                        .roleArn(ROLE_ARN)
                        .secretArn(SECRET_ARN)
                        .build())
                .build();

        model = ResourceModel.builder()
                .applicationId(APPLICATION_ID)
                .pluginId(PLUGIN_ID)
                .displayName(PLUGIN_NAME)
                .type(PLUGIN_TYPE)
                .state(PLUGIN_STATE)
                .buildStatus(PluginBuildStatus.READY.toString())
                .serverUrl(SERVER_URL)
                .authConfiguration(serviceAuthConfiguration)
                .createdAt(Instant.ofEpochMilli(CREATED_TIME).toString())
                .updatedAt(Instant.ofEpochMilli(UPDATED_TIME).toString())
                .tags(List.of(Tag.builder()
                        .key("Tag 1")
                        .value("Tag 2")
                        .build()))
                .build();

        request = ResourceHandlerRequest.<ResourceModel>builder()
                  .awsPartition(AWS_PARTITION)
                  .region(REGION)
                  .awsAccountId(ACCOUNT_ID)
                  .desiredResourceState(model)
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

      when(proxyClient.client().createPlugin(any(CreatePluginRequest.class)))
          .thenReturn(CreatePluginResponse.builder()
              .pluginId(PLUGIN_ID)
              .build());
      when(proxyClient.client().getPlugin(any(GetPluginRequest.class)))
          .thenReturn(GetPluginResponse.builder()
                .applicationId(APPLICATION_ID)
                .pluginId(PLUGIN_ID)
                .displayName(PLUGIN_NAME)
                .type(PLUGIN_TYPE)
                .state(PLUGIN_STATE)
                .buildStatus(PluginBuildStatus.READY)
                .serverUrl(SERVER_URL)
                .authConfiguration(cfnAuthConfiguration)
                .createdAt(Instant.ofEpochMilli(CREATED_TIME))
                .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
              .build());
      when(qBusinessClient.updatePlugin(any(UpdatePluginRequest.class))).thenReturn(UpdatePluginResponse.builder().build());
      when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenReturn(ListTagsForResourceResponse.builder()
            .tags(List.of(software.amazon.awssdk.services.qbusiness.model.Tag.builder()
                .key("Tag 1")
                .value("Tag 2")
                .build()))
            .build());

      final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

      verify(qBusinessClient).createPlugin(any(CreatePluginRequest.class));
      verify(qBusinessClient, times(2)).getPlugin(any(GetPluginRequest.class));
      verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));
      verify(qBusinessClient).updatePlugin(
          argThat(
              (ArgumentMatcher<UpdatePluginRequest>) t -> t.stateAsString().equals(PLUGIN_STATE) &&
                  t.applicationId().equals(APPLICATION_ID) &&
                  t.pluginId().equals(PLUGIN_ID)
          )
      );

      assertThat(response).isNotNull();
      assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
      assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
      assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
      assertThat(response.getResourceModels()).isNull();
      assertThat(response.getMessage()).isNull();
      assertThat(response.getErrorCode()).isNull();

      ResourceModel resultModel = response.getResourceModel();

      assertThat(resultModel.getApplicationId()).isEqualTo(APPLICATION_ID);
      assertThat(resultModel.getPluginId()).isEqualTo(PLUGIN_ID);
      assertThat(resultModel.getType()).isEqualTo(PLUGIN_TYPE);
      assertThat(resultModel.getState()).isEqualTo(PLUGIN_STATE);
      assertThat(resultModel.getBuildStatus()).isEqualTo(PluginBuildStatus.READY.toString());
      assertThat(resultModel.getDisplayName()).isEqualTo(PLUGIN_NAME);
      assertThat(resultModel.getAuthConfiguration()).isEqualTo(serviceAuthConfiguration);
      assertThat(resultModel.getCreatedAt()).isEqualTo(Instant.ofEpochMilli(CREATED_TIME).toString());
      assertThat(resultModel.getUpdatedAt()).isEqualTo(Instant.ofEpochMilli(UPDATED_TIME).toString());
    }

    @Test
    public void handleRequest_StabilizeFromCreateInProgressToReady() {

        when(proxyClient.client().createPlugin(any(CreatePluginRequest.class)))
                .thenReturn(CreatePluginResponse.builder()
                        .pluginId(PLUGIN_ID)
                        .build());
        when(proxyClient.client().getPlugin(any(GetPluginRequest.class)))
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .displayName(PLUGIN_NAME)
                        .type(PLUGIN_TYPE)
                        .state(PLUGIN_STATE)
                        .buildStatus(PluginBuildStatus.CREATE_IN_PROGRESS)
                        .serverUrl(SERVER_URL)
                        .authConfiguration(cfnAuthConfiguration)
                        .createdAt(Instant.ofEpochMilli(CREATED_TIME))
                        .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
                        .build())
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .displayName(PLUGIN_NAME)
                        .type(PLUGIN_TYPE)
                        .state(PLUGIN_STATE)
                        .buildStatus(PluginBuildStatus.READY)
                        .serverUrl(SERVER_URL)
                        .authConfiguration(cfnAuthConfiguration)
                        .createdAt(Instant.ofEpochMilli(CREATED_TIME))
                        .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
                        .build());
        when(qBusinessClient.updatePlugin(any(UpdatePluginRequest.class))).thenReturn(UpdatePluginResponse.builder().build());
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(ListTagsForResourceResponse.builder()
                        .tags(List.of(software.amazon.awssdk.services.qbusiness.model.Tag.builder()
                                .key("Tag 1")
                                .value("Tag 2")
                                .build()))
                        .build());

        final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(qBusinessClient).createPlugin(any(CreatePluginRequest.class));
        verify(qBusinessClient, times(3)).getPlugin(any(GetPluginRequest.class));
        verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(qBusinessClient).updatePlugin(
                argThat(
                        (ArgumentMatcher<UpdatePluginRequest>) t -> t.stateAsString().equals(PLUGIN_STATE) &&
                                t.applicationId().equals(APPLICATION_ID) &&
                                t.pluginId().equals(PLUGIN_ID)
                )
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        ResourceModel resultModel = response.getResourceModel();

        assertThat(resultModel.getApplicationId()).isEqualTo(APPLICATION_ID);
        assertThat(resultModel.getPluginId()).isEqualTo(PLUGIN_ID);
        assertThat(resultModel.getType()).isEqualTo(PLUGIN_TYPE);
        assertThat(resultModel.getState()).isEqualTo(PLUGIN_STATE);
        assertThat(resultModel.getBuildStatus()).isEqualTo(PluginBuildStatus.READY.toString());
        assertThat(resultModel.getDisplayName()).isEqualTo(PLUGIN_NAME);
        assertThat(resultModel.getAuthConfiguration()).isEqualTo(serviceAuthConfiguration);
        assertThat(resultModel.getCreatedAt()).isEqualTo(Instant.ofEpochMilli(CREATED_TIME).toString());
        assertThat(resultModel.getUpdatedAt()).isEqualTo(Instant.ofEpochMilli(UPDATED_TIME).toString());
    }

    @Test
    public void handleRequest_ThrowsExpectedErrorWhenStabilizationFails() {

        when(proxyClient.client().createPlugin(any(CreatePluginRequest.class)))
                .thenReturn(CreatePluginResponse.builder()
                        .pluginId(PLUGIN_ID)
                        .build());
        when(proxyClient.client().getPlugin(any(GetPluginRequest.class)))
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .displayName(PLUGIN_NAME)
                        .type(PLUGIN_TYPE)
                        .state(PLUGIN_STATE)
                        .buildStatus(PluginBuildStatus.CREATE_FAILED)
                        .serverUrl(SERVER_URL)
                        .authConfiguration(cfnAuthConfiguration)
                        .createdAt(Instant.ofEpochMilli(CREATED_TIME))
                        .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
                        .build());

        assertThatThrownBy(() -> underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnNotStabilizedException.class);

        verify(qBusinessClient).createPlugin(any(CreatePluginRequest.class));
        verify(qBusinessClient).getPlugin(any(GetPluginRequest.class));
    }

    @Test
    public void testThatItDoesNotCallUpdatePluginIfStateIsNotSet() {
      // set up
      model = model.toBuilder().state(null).build();
      request.setDesiredResourceState(model);
      when(qBusinessClient.createPlugin(any(CreatePluginRequest.class)))
          .thenReturn(CreatePluginResponse.builder()
              .pluginId(PLUGIN_ID)
              .build());
      when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
          .thenReturn(GetPluginResponse.builder()
              .applicationId(APPLICATION_ID)
              .pluginId(PLUGIN_ID)
              .displayName(PLUGIN_NAME)
              .type(PLUGIN_TYPE)
              .state(PLUGIN_STATE)
              .buildStatus(PluginBuildStatus.READY)
              .serverUrl(SERVER_URL)
              .authConfiguration(cfnAuthConfiguration)
              .createdAt(Instant.ofEpochMilli(CREATED_TIME))
              .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
              .build());
      when(qBusinessClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
          .thenReturn(ListTagsForResourceResponse.builder()
              .tags(List.of())
              .build());

      // call method under test
      final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

      // verify results
      verify(qBusinessClient).createPlugin(any(CreatePluginRequest.class));
      verify(qBusinessClient, times(2)).getPlugin(any(GetPluginRequest.class));
      verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));

      assertThat(response).isNotNull();
      assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    }

    private static Stream<Arguments> serviceErrorAndHandlerCodes() {
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
  @MethodSource("serviceErrorAndHandlerCodes")
  public void testThatItReturnsExpectedErrorCode(QBusinessException serviceError, HandlerErrorCode cfnErrorCode) {

    // set up test
    when(proxyClient.client().createPlugin(any(CreatePluginRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();

    verify(qBusinessClient).createPlugin(any(CreatePluginRequest.class));
  }
}
