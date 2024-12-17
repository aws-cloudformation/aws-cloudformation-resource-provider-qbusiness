package software.amazon.qbusiness.webexperience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.awssdk.services.qbusiness.model.WebExperienceStatus;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "63451660-1596-4f1a-a3c8-e5f4b33d9fe5";
  private static final String WEB_EXPERIENCE_ID = "11111111-1596-4f1a-a3c8-e5f4b33d9fe5";
  private static final List<String> ORIGINS_URL = List.of("https://someTestOriginUrl.com/");
  private static final String CUSTOM_CSS_URL = "https://someTest-public.s3.us-west-2.amazonaws.com/test.css";
  private static final String LOGO_URL = "https://someTest-public.s3.us-west-2.amazonaws.com/test.png";
  private static final String FONT_URL = "https://someTest-public.s3.us-west-2.amazonaws.com/test.otf";
  private static final String FAVICON_URL = "https://someTest-public.s3.us-west-2.amazonaws.com/test.ico";
  private software.amazon.awssdk.services.qbusiness.model.CustomizationConfiguration customizationConfiguration;

  private AmazonWebServicesClientProxy proxy;

  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private ReadHandler underTest;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel model;

  private AutoCloseable testMocks;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);

    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    proxyClient = MOCK_PROXY(proxy, sdkClient);
    underTest = new ReadHandler();

    model = ResourceModel.builder()
        .applicationId(APP_ID)
        .webExperienceId(WEB_EXPERIENCE_ID)
        .build();
    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .awsAccountId("123456")
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
        .build();

    customizationConfiguration = software.amazon.awssdk.services.qbusiness.model.CustomizationConfiguration.builder()
        .customCSSUrl(CUSTOM_CSS_URL)
        .logoUrl(LOGO_URL)
        .fontUrl(FONT_URL)
        .faviconUrl(FAVICON_URL)
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
    // set up test scenario
    when(sdkClient.getWebExperience(any(GetWebExperienceRequest.class)))
        .thenReturn(GetWebExperienceResponse.builder()
            .applicationId(APP_ID)
            .webExperienceId(WEB_EXPERIENCE_ID)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .title("This is a title of the web experience.")
            .subtitle("This is a subtitle of the web experience.")
            .status(WebExperienceStatus.ACTIVE)
            .roleArn("RoleArn")
            .defaultEndpoint("Endpoint")
            .origins(ORIGINS_URL)
            .customizationConfiguration(customizationConfiguration)
            .build());
    when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenReturn(ListTagsForResourceResponse.builder()
            .tags(List.of(software.amazon.awssdk.services.qbusiness.model.Tag.builder()
                .key("Category")
                .value("Chat Stuff")
                .build()))
            .build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify result
    verify(sdkClient).getWebExperience(any(GetWebExperienceRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(responseProgress.getResourceModels()).isNull();
    assertThat(responseProgress.getMessage()).isNull();
    assertThat(responseProgress.getErrorCode()).isNull();
    ResourceModel resultModel = responseProgress.getResourceModel();
    assertThat(resultModel.getApplicationId()).isEqualTo(APP_ID);
    assertThat(resultModel.getWebExperienceId()).isEqualTo(WEB_EXPERIENCE_ID);
    assertThat(resultModel.getCreatedAt()).isEqualTo("2023-10-20T18:02:15Z");
    assertThat(resultModel.getUpdatedAt()).isEqualTo("2023-10-20T22:02:15Z");
    assertThat(resultModel.getTitle()).isEqualTo("This is a title of the web experience.");
    assertThat(resultModel.getSubtitle()).isEqualTo("This is a subtitle of the web experience.");
    assertThat(resultModel.getStatus()).isEqualTo(WebExperienceStatus.ACTIVE.toString());
    assertThat(resultModel.getRoleArn()).isEqualTo("RoleArn");
    assertThat(resultModel.getDefaultEndpoint()).isEqualTo("Endpoint");
    assertThat(resultModel.getOrigins()).isEqualTo(ORIGINS_URL);
    assertThat(resultModel.getCustomizationConfiguration()).isEqualTo(CustomizationConfiguration.builder()
        .customCSSUrl(CUSTOM_CSS_URL)
        .logoUrl(LOGO_URL)
        .fontUrl(FONT_URL)
        .faviconUrl(FAVICON_URL)
        .build());

    var tags = resultModel.getTags().stream().map(tag -> Map.entry(tag.getKey(), tag.getValue())).toList();
    assertThat(tags).isEqualTo(List.of(
        Map.entry("Category", "Chat Stuff")
    ));
  }

  @Test
  public void handleRequest_SimpleSuccess_withMissingProperties() {
    // set up test scenario
    when(sdkClient.getWebExperience(any(GetWebExperienceRequest.class)))
        .thenReturn(GetWebExperienceResponse.builder()
            .applicationId(APP_ID)
            .webExperienceId(WEB_EXPERIENCE_ID)
            .title("This is a title of the web experience.")
            .status(WebExperienceStatus.ACTIVE)
            .roleArn("RoleArn")
            .build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify result
    verify(sdkClient).getWebExperience(any(GetWebExperienceRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(responseProgress.getResourceModels()).isNull();
    assertThat(responseProgress.getMessage()).isNull();
    assertThat(responseProgress.getErrorCode()).isNull();

    ResourceModel resultModel = responseProgress.getResourceModel();
    assertThat(resultModel.getCreatedAt()).isNull();
    assertThat(resultModel.getUpdatedAt()).isNull();
    assertThat(resultModel.getSubtitle()).isNull();
    assertThat(resultModel.getDefaultEndpoint()).isNull();
    assertThat(resultModel.getTitle()).isEqualTo("This is a title of the web experience.");
    assertThat(resultModel.getApplicationId()).isEqualTo(APP_ID);
    assertThat(resultModel.getWebExperienceId()).isEqualTo(WEB_EXPERIENCE_ID);
    assertThat(resultModel.getRoleArn()).isEqualTo("RoleArn");
  }

  private static Stream<Arguments> serviceErrorAndExpectedCfnCode() {
    return Stream.of(
        Arguments.of(ValidationException.builder().message("nopes").build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ResourceNotFoundException.builder().message("404").build(), HandlerErrorCode.NotFound),
        Arguments.of(ThrottlingException.builder().message("too much").build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().message("denied!").build(), HandlerErrorCode.AccessDenied),
        Arguments.of(InternalServerException.builder().message("something happened").build(), HandlerErrorCode.GeneralServiceException)
    );
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCode(QBusinessException serviceError, HandlerErrorCode cfnErrorCode) {
    when(proxyClient.client().getWebExperience(any(GetWebExperienceRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getWebExperience(any(GetWebExperienceRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCodeWhenListTagsForResourceFails(
      QBusinessException serviceError,
      HandlerErrorCode cfnErrorCode) {
    // set up test scenario
    when(sdkClient.getWebExperience(any(GetWebExperienceRequest.class)))
        .thenReturn(GetWebExperienceResponse.builder()
            .applicationId(APP_ID)
            .webExperienceId(WEB_EXPERIENCE_ID)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .title("This is a title of the web experience.")
            .subtitle("This is a subtitle of the web experience.")
            .status(WebExperienceStatus.ACTIVE)
            .roleArn("RoleArn")
            .defaultEndpoint("Endpoint")
            .build());

    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getWebExperience(any(GetWebExperienceRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }
}
