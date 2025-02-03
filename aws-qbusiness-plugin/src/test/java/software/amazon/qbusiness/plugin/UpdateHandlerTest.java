package software.amazon.qbusiness.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.GetPluginRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.PluginBuildStatus;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginResponse;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class UpdateHandlerTest extends AbstractTestBase {

    private static final String APPLICATION_ID = "ApplicationId";
    private static final String PLUGIN_ID = "PluginId";
    private static final String PLUGIN_NAME = "PluginName";
    private static final String PLUGIN_TYPE = "JIRA";
    private static final String PLUGIN_STATE = "ACTIVE";
    private static final String SERVER_URL = "ServerUrl";
    private static final String ROLE_ARN = "role-1";
    private static final String SECRET_ARN = "secret-1";
    private static final String UPDATED_PLUGIN_NAME = "UpdatedPluginName";
    private static final String UPDATED_PLUGIN_STATE = "INACTIVE";
    private static final String UPDATED_SERVER_URL = "UpdatedServerUrl";
    private static final String UPDATED_ROLE_ARN = "updated-role-1";
    private static final String UPDATED_SECRET_ARN = "updated-secret-1";
    private static final String CLIENT_TOKEN = "client-token";
    private static final String AWS_PARTITION = "aws";
    private static final String ACCOUNT_ID = "123456789012";
    private static final String REGION = "us-west-2";

    private AmazonWebServicesClientProxy proxy;

    private ProxyClient<QBusinessClient> proxyClient;

    @Mock
    private QBusinessClient qBusinessClient;

    private AutoCloseable testMocks;
    private UpdateHandler underTest;
    private PluginAuthConfiguration serviceAuthConfiguration;
    private PluginAuthConfiguration updatedServiceAuthConfiguration;
    private software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration cfnAuthConfiguration;
    private software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration updatedCfnAuthConfiguration;
    private ResourceModel model;
    private ResourceModel updatedModel;
    private ResourceHandlerRequest<ResourceModel> request;

    @BeforeEach
    public void setup() {
        testMocks = MockitoAnnotations.openMocks(this);
        var testBackOff = Constant.of()
            .delay(Duration.ofSeconds(5))
            .timeout(Duration.ofSeconds(45))
        .build();
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        this.underTest = new UpdateHandler(testBackOff);

        serviceAuthConfiguration = PluginAuthConfiguration.builder()
                .basicAuthConfiguration(BasicAuthConfiguration.builder()
                        .roleArn(ROLE_ARN)
                        .secretArn(SECRET_ARN)
                        .build())
                .build();

        updatedServiceAuthConfiguration = PluginAuthConfiguration.builder()
                .basicAuthConfiguration(BasicAuthConfiguration.builder()
                        .roleArn(UPDATED_ROLE_ARN)
                        .secretArn(UPDATED_SECRET_ARN)
                        .build())
                .build();

        cfnAuthConfiguration = software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration.builder()
                .basicAuthConfiguration(software.amazon.awssdk.services.qbusiness.model.BasicAuthConfiguration.builder()
                        .roleArn(ROLE_ARN)
                        .secretArn(SECRET_ARN)
                        .build())
                .build();

        updatedCfnAuthConfiguration = software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration.builder()
                .basicAuthConfiguration(software.amazon.awssdk.services.qbusiness.model.BasicAuthConfiguration.builder()
                        .secretArn(UPDATED_SECRET_ARN)
                        .roleArn(UPDATED_ROLE_ARN)
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
                    .tags(List.of(
                        Tag.builder().key("remain").value("thesame").build(),
                        Tag.builder().key("toremove").value("nolongerthere").build(),
                        Tag.builder().key("iwillchange").value("oldvalue").build()
                    ))
                .build();

        updatedModel = ResourceModel.builder()
                    .applicationId(APPLICATION_ID)
                    .pluginId(PLUGIN_ID)
                    .displayName(UPDATED_PLUGIN_NAME)
                    .type(PLUGIN_TYPE)
                    .state(UPDATED_PLUGIN_STATE)
                    .buildStatus(PluginBuildStatus.READY.toString())
                    .serverUrl(UPDATED_SERVER_URL)
                    .authConfiguration(updatedServiceAuthConfiguration)
                    .tags(List.of(
                        Tag.builder().key("remain").value("thesame").build(),
                        Tag.builder().key("iwillchange").value("nowanewvalue").build(),
                        Tag.builder().key("iamnew").value("overhere").build()
                    ))
                .build();

       request = ResourceHandlerRequest.<ResourceModel>builder()
              .awsPartition(AWS_PARTITION)
              .region(REGION)
              .awsAccountId(ACCOUNT_ID)
              .systemTags(Map.of(
                  "aws::cloudformation::created", "onthisday"
              ))
              .previousSystemTags(Map.of(
                  "aws::cloudformation::created", "onthisday"
              ))
              .previousResourceTags(Map.of(
                  "stacksame", "value",
                  "stackchange", "whowho",
                  "stack-i-remove", "hello"
              ))
              .desiredResourceTags(Map.of(
                  "stacksame", "value",
                  "stackchange", "whatwhenwhere",
                  "stacknewaddition", "newvalue"
              ))
              .previousResourceState(model)
              .desiredResourceState(updatedModel)
              .clientRequestToken(CLIENT_TOKEN)
            .build();

        when(proxyClient.client().updatePlugin(any(UpdatePluginRequest.class)))
          .thenReturn(UpdatePluginResponse.builder()
              .build());

         when(qBusinessClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
          .thenReturn(ListTagsForResourceResponse.builder().tags(List.of()).build());
    }

    @AfterEach
    public void tear_down() throws Exception {
        verify(qBusinessClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(qBusinessClient);
        testMocks.close();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
      when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
        .thenReturn(GetPluginResponse.builder()
                  .applicationId(APPLICATION_ID)
                  .pluginId(PLUGIN_ID)
                  .displayName(UPDATED_PLUGIN_NAME)
                  .type(PLUGIN_TYPE)
                  .state(UPDATED_PLUGIN_STATE)
                  .buildStatus(PluginBuildStatus.READY)
                  .serverUrl(UPDATED_SERVER_URL)
                  .authConfiguration(updatedCfnAuthConfiguration)
            .build());
      when(qBusinessClient.tagResource(any(TagResourceRequest.class)))
          .thenReturn(TagResourceResponse.builder().build());
      when(qBusinessClient.untagResource(any(UntagResourceRequest.class)))
          .thenReturn(UntagResourceResponse.builder().build());
      final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
          proxy, request, new CallbackContext(), proxyClient, logger
      );

      assertThat(resultProgress).isNotNull();
      assertThat(resultProgress.isSuccess()).isTrue();
      ArgumentCaptor<UpdatePluginRequest> updatePluginReqCaptor = ArgumentCaptor.forClass(UpdatePluginRequest.class);
      verify(qBusinessClient).updatePlugin(updatePluginReqCaptor.capture());
      assertThat(resultProgress.getResourceModel().getApplicationId()).isEqualTo(APPLICATION_ID);
      assertThat(resultProgress.getResourceModel().getPluginId()).isEqualTo(PLUGIN_ID);
      assertThat(resultProgress.getResourceModel().getAuthConfiguration().getBasicAuthConfiguration().getRoleArn())
              .isEqualTo(AuthConfigHelper.convertToServiceAuthConfig(updatedModel.getAuthConfiguration())
                      .basicAuthConfiguration().roleArn());
      assertThat(resultProgress.getResourceModel().getAuthConfiguration().getBasicAuthConfiguration().getSecretArn())
              .isEqualTo(AuthConfigHelper.convertToServiceAuthConfig(updatedModel.getAuthConfiguration())
                       .basicAuthConfiguration().secretArn());
      assertThat(resultProgress.getResourceModel().getDisplayName()).isEqualTo(UPDATED_PLUGIN_NAME);
      assertThat(resultProgress.getResourceModel().getState()).isEqualTo(UPDATED_PLUGIN_STATE);
      assertThat(resultProgress.getResourceModel().getServerUrl()).isEqualTo(UPDATED_SERVER_URL);

      verify(qBusinessClient, times(2)).getPlugin(
          argThat((ArgumentMatcher<GetPluginRequest>) t ->
              t.applicationId().equals(APPLICATION_ID) && t.pluginId().equals(PLUGIN_ID)
          )
      );
      verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));

      var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
      var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
      verify(qBusinessClient).tagResource(tagReqCaptor.capture());
      verify(qBusinessClient).untagResource(untagReqCaptor.capture());

      var tagResourceRequest = tagReqCaptor.getValue();
      Map<String, String> tagsInTagResourceReq = tagResourceRequest.tags().stream()
          .collect(Collectors.toMap(tag -> tag.key(), tag -> tag.value()));
      assertThat(tagsInTagResourceReq).containsOnly(
          Map.entry("iwillchange", "nowanewvalue"),
          Map.entry("iamnew", "overhere"),
          Map.entry("stackchange", "whatwhenwhere"),
          Map.entry("stacknewaddition", "newvalue")
      );

      var untagResourceReq = untagReqCaptor.getValue();
      assertThat(untagResourceReq.tagKeys()).containsOnlyOnceElementsOf(List.of(
          "toremove",
          "stack-i-remove"
      ));
    }

    @Test
    public void handleRequest_StabilizeFromUpdateInProgressToReady() {
        when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .displayName(UPDATED_PLUGIN_NAME)
                        .type(PLUGIN_TYPE)
                        .state(UPDATED_PLUGIN_STATE)
                        .buildStatus(PluginBuildStatus.UPDATE_IN_PROGRESS)
                        .serverUrl(UPDATED_SERVER_URL)
                        .authConfiguration(updatedCfnAuthConfiguration)
                        .build())
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .displayName(UPDATED_PLUGIN_NAME)
                        .type(PLUGIN_TYPE)
                        .state(UPDATED_PLUGIN_STATE)
                        .buildStatus(PluginBuildStatus.READY)
                        .serverUrl(UPDATED_SERVER_URL)
                        .authConfiguration(updatedCfnAuthConfiguration)
                        .build());

        when(qBusinessClient.tagResource(any(TagResourceRequest.class)))
                .thenReturn(TagResourceResponse.builder().build());
        when(qBusinessClient.untagResource(any(UntagResourceRequest.class)))
                .thenReturn(UntagResourceResponse.builder().build());
        final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
                proxy, request, new CallbackContext(), proxyClient, logger
        );

        assertThat(resultProgress).isNotNull();
        assertThat(resultProgress.isSuccess()).isTrue();
        ArgumentCaptor<UpdatePluginRequest> updatePluginReqCaptor = ArgumentCaptor.forClass(UpdatePluginRequest.class);
        verify(qBusinessClient).updatePlugin(updatePluginReqCaptor.capture());
        assertThat(resultProgress.getResourceModel().getApplicationId()).isEqualTo(APPLICATION_ID);
        assertThat(resultProgress.getResourceModel().getPluginId()).isEqualTo(PLUGIN_ID);
        assertThat(resultProgress.getResourceModel().getAuthConfiguration().getBasicAuthConfiguration().getRoleArn())
                .isEqualTo(AuthConfigHelper.convertToServiceAuthConfig(updatedModel.getAuthConfiguration())
                        .basicAuthConfiguration().roleArn());
        assertThat(resultProgress.getResourceModel().getAuthConfiguration().getBasicAuthConfiguration().getSecretArn())
                .isEqualTo(AuthConfigHelper.convertToServiceAuthConfig(updatedModel.getAuthConfiguration())
                        .basicAuthConfiguration().secretArn());
        assertThat(resultProgress.getResourceModel().getDisplayName()).isEqualTo(UPDATED_PLUGIN_NAME);
        assertThat(resultProgress.getResourceModel().getState()).isEqualTo(UPDATED_PLUGIN_STATE);
        assertThat(resultProgress.getResourceModel().getServerUrl()).isEqualTo(UPDATED_SERVER_URL);

        verify(qBusinessClient, times(3)).getPlugin(
                argThat((ArgumentMatcher<GetPluginRequest>) t ->
                        t.applicationId().equals(APPLICATION_ID) && t.pluginId().equals(PLUGIN_ID)
                )
        );
        verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));

        var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
        var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(qBusinessClient).tagResource(tagReqCaptor.capture());
        verify(qBusinessClient).untagResource(untagReqCaptor.capture());

        var tagResourceRequest = tagReqCaptor.getValue();
        Map<String, String> tagsInTagResourceReq = tagResourceRequest.tags().stream()
                .collect(Collectors.toMap(tag -> tag.key(), tag -> tag.value()));
        assertThat(tagsInTagResourceReq).containsOnly(
                Map.entry("iwillchange", "nowanewvalue"),
                Map.entry("iamnew", "overhere"),
                Map.entry("stackchange", "whatwhenwhere"),
                Map.entry("stacknewaddition", "newvalue")
        );

        var untagResourceReq = untagReqCaptor.getValue();
        assertThat(untagResourceReq.tagKeys()).containsOnlyOnceElementsOf(List.of(
                "toremove",
                "stack-i-remove"
        ));
    }

    @Test
    public void handleRequest_ThrowsExpectedErrorWhenStabilizationFails() {
        when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
                .thenReturn(GetPluginResponse.builder()
                        .applicationId(APPLICATION_ID)
                        .pluginId(PLUGIN_ID)
                        .displayName(UPDATED_PLUGIN_NAME)
                        .type(PLUGIN_TYPE)
                        .state(UPDATED_PLUGIN_STATE)
                        .buildStatus(PluginBuildStatus.UPDATE_FAILED)
                        .serverUrl(UPDATED_SERVER_URL)
                        .authConfiguration(updatedCfnAuthConfiguration)
                        .build());

        assertThatThrownBy(() -> underTest.handleRequest(
                proxy, request, new CallbackContext(), proxyClient, logger
        )).isInstanceOf(CfnNotStabilizedException.class);

        verify(qBusinessClient).updatePlugin(any(UpdatePluginRequest.class));
        verify(qBusinessClient, times(1)).getPlugin(
                argThat((ArgumentMatcher<GetPluginRequest>) t ->
                        t.applicationId().equals(APPLICATION_ID) && t.pluginId().equals(PLUGIN_ID)
                )
        );
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

  @Test
  public void testThatItDoesntTagAndUnTag() {
    when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
        .thenReturn(GetPluginResponse.builder()
                  .applicationId(APPLICATION_ID)
                  .pluginId(PLUGIN_ID)
                  .displayName(UPDATED_PLUGIN_NAME)
                  .type(PLUGIN_TYPE)
                  .state(UPDATED_PLUGIN_STATE)
                  .buildStatus(PluginBuildStatus.READY)
                  .serverUrl(UPDATED_SERVER_URL)
                  .authConfiguration(updatedCfnAuthConfiguration)
            .build());
    when(qBusinessClient.tagResource(any(TagResourceRequest.class)))
        .thenReturn(TagResourceResponse.builder().build());
    request.setPreviousResourceTags(Map.of(
        "stacksame", "value"
    ));
    request.setDesiredResourceTags(Map.of(
        "stacksame", "newValue"
    ));
    model.setTags(List.of(
        Tag.builder().key("datTag").value("valuea").build()
    ));
    updatedModel.setTags(List.of(
        Tag.builder().key("datTag").value("valueb").build()
    ));

    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(qBusinessClient).updatePlugin(any(UpdatePluginRequest.class));

    verify(qBusinessClient, times(2)).getPlugin(
        argThat((ArgumentMatcher<GetPluginRequest>) t ->
          t.applicationId().equals(APPLICATION_ID) && t.pluginId().equals(PLUGIN_ID)
        )
    );
    verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(qBusinessClient).tagResource(tagReqCaptor.capture());

    var tagRequest = tagReqCaptor.getValue();
    Map<String, String> tagsAddedInReq = tagRequest.tags().stream()
        .collect(Collectors.toMap(tag -> tag.key(), tag -> tag.value()));

    assertThat(tagsAddedInReq.entrySet()).isEqualTo(Set.of(
        Map.entry("stacksame", "newValue"),
        Map.entry("datTag", "valueb")
    ));
  }

  @Test
  public void testThatItCallsUnTagButSkipsCallingTag() {
    when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
        .thenReturn(GetPluginResponse.builder()
                  .applicationId(APPLICATION_ID)
                  .pluginId(PLUGIN_ID)
                  .displayName(UPDATED_PLUGIN_NAME)
                  .type(PLUGIN_TYPE)
                  .state(UPDATED_PLUGIN_STATE)
                  .buildStatus(PluginBuildStatus.READY)
                  .serverUrl(UPDATED_SERVER_URL)
                  .authConfiguration(updatedCfnAuthConfiguration)
            .build());
    when(qBusinessClient.untagResource(any(UntagResourceRequest.class)))
        .thenReturn(UntagResourceResponse.builder().build());
    model.setTags(List.of(
        Tag.builder().key("remain").value("thesame").build(),
        Tag.builder().key("toBeRemove").value("theValue").build()
    ));
    updatedModel.setTags(List.of(
        Tag.builder().key("remain").value("thesame").build()
    ));
    request.setPreviousResourceTags(Map.of(
        "stacksame", "value"
    ));
    request.setDesiredResourceTags(Map.of(
        "stacksame", "value"
    ));

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(qBusinessClient).updatePlugin(any(UpdatePluginRequest.class));
    verify(qBusinessClient, times(2)).getPlugin(
        argThat((ArgumentMatcher<GetPluginRequest>) t ->
            t.applicationId().equals(APPLICATION_ID) && t.pluginId().equals(PLUGIN_ID)
        )
    );
    verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(qBusinessClient).untagResource(untagReqCaptor.capture());

    verify(qBusinessClient, times(0)).tagResource(any(TagResourceRequest.class));

    var untagReq = untagReqCaptor.getValue();
    assertThat(untagReq.tagKeys()).isEqualTo(List.of("toBeRemove"));
  }

  private static Stream<Arguments> tagAndUntagArguments() {
    return Stream.of(
        Arguments.of(
            null,
            Map.of("datTag", "suchValue"),
            Map.of("stacksame", "newValue")
        ),
        Arguments.of(
            List.of(Tag.builder().key("datTag").value("valuea").build()),
            null,
            Map.of("stacksame", "newValue")
        ),
        Arguments.of(
            List.of(Tag.builder().key("datTag").value("valuea").build()),
            Map.of("stacksame", "newValue"),
            null
        ),
        Arguments.of(
            null,
            null,
            null
        )
    );
  }

  @ParameterizedTest
  @MethodSource("tagAndUntagArguments")
  public void testThatItDoesntTagAndUnTagWhenTagFieldsAreNull(
      List<Tag> modelTags,
      Map<String, String> reqTags,
      Map<String, String> sysTags
  ) {
    when(qBusinessClient.getPlugin(any(GetPluginRequest.class)))
        .thenReturn(GetPluginResponse.builder()
                  .applicationId(APPLICATION_ID)
                  .pluginId(PLUGIN_ID)
                  .displayName(UPDATED_PLUGIN_NAME)
                  .type(PLUGIN_TYPE)
                  .state(UPDATED_PLUGIN_STATE)
                  .buildStatus(PluginBuildStatus.READY)
                  .serverUrl(UPDATED_SERVER_URL)
                  .authConfiguration(updatedCfnAuthConfiguration)
            .build());
    // set up test scenario
    model.setTags(modelTags);
    updatedModel.setTags(modelTags);
    request.setPreviousResourceTags(reqTags);
    request.setDesiredResourceTags(reqTags);
    request.setPreviousSystemTags(sysTags);
    request.setSystemTags(sysTags);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(qBusinessClient).updatePlugin(any(UpdatePluginRequest.class));
    verify(qBusinessClient, times(2)).getPlugin(
        argThat((ArgumentMatcher<GetPluginRequest>) t ->
            t.applicationId().equals(APPLICATION_ID) && t.pluginId().equals(PLUGIN_ID)
        )
    );
    verify(qBusinessClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

}
