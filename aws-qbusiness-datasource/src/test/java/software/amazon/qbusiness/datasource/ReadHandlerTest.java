package software.amazon.qbusiness.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static software.amazon.qbusiness.datasource.translators.DocumentConverter.convertToMapToDocument;

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
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.DataSourceVpcConfiguration;
import software.amazon.awssdk.services.qbusiness.model.DocumentAttributeCondition;
import software.amazon.awssdk.services.qbusiness.model.DocumentAttributeTarget;
import software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue;
import software.amazon.awssdk.services.qbusiness.model.DocumentContentOperator;
import software.amazon.awssdk.services.qbusiness.model.DocumentEnrichmentConditionOperator;
import software.amazon.awssdk.services.qbusiness.model.DocumentEnrichmentConfiguration;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.HookConfiguration;
import software.amazon.awssdk.services.qbusiness.model.InlineDocumentEnrichmentConfiguration;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "3f7bf9f2-205f-47af-8e35-804fc749fbf8";
  private static final String INDEX_ID = "31b1150f-4988-4d79-8952-8cac97d54322";
  private static final String DATA_SOURCE_ID = "b1d8bb25-bd6f-4bfd-8286-475b029b52a8";

  private AmazonWebServicesClientProxy proxy;
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testAutoCloseable;

  private ReadHandler underTest;
  private ResourceHandlerRequest<ResourceModel> request;
  private ResourceModel model;

  @BeforeEach
  public void setup() {
    testAutoCloseable = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    proxyClient = MOCK_PROXY(proxy, sdkClient);
    underTest = new ReadHandler();

    model = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
        .build();

    request = ResourceHandlerRequest.<ResourceModel>builder()
        .awsPartition("aws")
        .region("us-west-2")
        .awsAccountId("111122223333")
        .desiredResourceState(model)
        .build();
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);

    testAutoCloseable.close();
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenReturn(
        GetDataSourceResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .dataSourceId(DATA_SOURCE_ID)
            .displayName("WhatsInAName")
            .description("A rose by any other name smells just as sweet.")
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .status(DataSourceStatus.ACTIVE)
            .roleArn("role1")
            .syncSchedule("0 12 * * 3")
            .type("S3")
            .vpcConfiguration(DataSourceVpcConfiguration.builder()
                .securityGroupIds("sec1", "sec2")
                .subnetIds("sub1", "sub2")
                .build())
            .configuration(Document.fromMap((
                Map.of(
                    "BucketName", Document.fromString("TheBucket"),
                    "AnotherOne", Document.fromMap(Map.of(
                        "Hello", Document.fromString("World")))
                )))
            )
            .documentEnrichmentConfiguration(DocumentEnrichmentConfiguration.builder()
                .inlineConfigurations(InlineDocumentEnrichmentConfiguration.builder()
                    .target(DocumentAttributeTarget.builder()
                        .key("akey")
                        .value(DocumentAttributeValue.builder().stringValue("strval").build())
                        .build()
                    )
                    .documentContentOperator(DocumentContentOperator.DELETE)
                    .condition(DocumentAttributeCondition.builder()
                        .operator(DocumentEnrichmentConditionOperator.GREATER_THAN)
                        .key("theval")
                        .value(DocumentAttributeValue.builder().dateValue(Instant.ofEpochMilli(1699909984785L)).build())
                        .build())
                    .build()
                )
                .preExtractionHookConfiguration(HookConfiguration.builder()
                    .roleArn("enrichrole")
                    .lambdaArn("this-is-arn")
                    .s3BucketName("this-is-s3-buck")
                    .invocationCondition(DocumentAttributeCondition.builder()
                        .key("key")
                        .operator(DocumentEnrichmentConditionOperator.EQUALS)
                        .value(DocumentAttributeValue.builder().longValue(11L).build())
                        .build()
                    )
                    .build()
                )
                .postExtractionHookConfiguration(HookConfiguration.builder()
                    .roleArn("enrichrole")
                    .lambdaArn("this-is-arn")
                    .s3BucketName("this-is-s3-buck")
                    .invocationCondition(DocumentAttributeCondition.builder()
                        .key("key")
                        .operator(DocumentEnrichmentConditionOperator.EQUALS)
                        .value(DocumentAttributeValue.builder().stringListValue("World", "Woah").build())
                        .build()
                    )
                    .build()
                )
                .build()
            )
            .build()
    );

    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse.builder()
        .tags(List.of())
        .build()
    );

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    verify(sdkClient).getDataSource(
        argThat((ArgumentMatcher<GetDataSourceRequest>) t -> t.applicationId().equals(APP_ID) &&
            t.indexId().equals(INDEX_ID) && t.dataSourceId().equals(DATA_SOURCE_ID)
        )
    );

    var expectedArn = "arn:aws:qbusiness:us-west-2:111122223333:application/%s/index/%s/data-source/%s".formatted(APP_ID, INDEX_ID, DATA_SOURCE_ID);
    verify(sdkClient).listTagsForResource(argThat(
        (ArgumentMatcher<ListTagsForResourceRequest>) t -> t.resourceARN().equals(expectedArn)
    ));

    var resultModel = responseProgress.getResourceModel();
    Map<String, Object> template = resultModel.getConfiguration();
    assertThat(template.get("BucketName")).isEqualTo("TheBucket");
    assertThat(template.get("AnotherOne")).isEqualTo(Map.of(
        "Hello", "World"
    ));
  }

  private static Stream<Arguments> errorCodeExpects() {
    return Stream.of(
        Arguments.of(ValidationException.builder().build(), HandlerErrorCode.InvalidRequest),
        Arguments.of(ResourceNotFoundException.builder().build(), HandlerErrorCode.NotFound),
        Arguments.of(ThrottlingException.builder().build(), HandlerErrorCode.Throttling),
        Arguments.of(AccessDeniedException.builder().build(), HandlerErrorCode.AccessDenied),
        Arguments.of(InternalServerException.builder().build(), HandlerErrorCode.GeneralServiceException)
    );
  }

  @ParameterizedTest
  @MethodSource("errorCodeExpects")
  public void testThatItReturnsTheExpectedErrorCodeWhenGetDataSourceFails(
      QBusinessException serviceError,
      HandlerErrorCode expectedCfnErrorCode) {
    // set up
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenThrow(serviceError);

    // call
    var resultProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getDataSource(any(GetDataSourceRequest.class));
    assertThat(resultProgress.getErrorCode()).isEqualTo(expectedCfnErrorCode);
  }

  @ParameterizedTest
  @MethodSource("errorCodeExpects")
  public void testThatItReturnsExpectedErrorCodeWhenListTagsFails(
      QBusinessException serviceError,
      HandlerErrorCode expectedCfnErrorCode
  ) {
    // set up
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenReturn(GetDataSourceResponse.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
        .displayName("WhatsInAName")
        .description("A rose by any other name smells just as sweet.")
        .createdAt(Instant.ofEpochMilli(1697824935000L))
        .updatedAt(Instant.ofEpochMilli(1697839335000L))
        .status(DataSourceStatus.ACTIVE)
        .roleArn("role1")
        .syncSchedule("0 12 * * 3")
        .type("S3")
        .build()
    );
    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class))).thenThrow(serviceError);

    // call
    var resultProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getDataSource(any(GetDataSourceRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(resultProgress.getErrorCode()).isEqualTo(expectedCfnErrorCode);
  }
}
