package software.amazon.qbusiness.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.AttributeType;
import software.amazon.awssdk.services.qbusiness.model.QBusinessException;
import software.amazon.awssdk.services.qbusiness.model.GetIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.IndexStatus;
import software.amazon.awssdk.services.qbusiness.model.InternalServerException;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.Status;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "63451660-1596-4f1a-a3c8-e5f4b33d9fe5";
  private static final String INDEX_ID = "11111111-1596-4f1a-a3c8-e5f4b33d9fe5";

  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private ReadHandler underTest;

  ResourceHandlerRequest<ResourceModel> testRequest;
  ResourceModel model;

  @BeforeEach
  public void setup() {
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);
    underTest = new ReadHandler();

    model = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .build();
    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .desiredResourceState(model)
        .awsAccountId("123456")
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
        .build();
  }

  @AfterEach
  public void tear_down() {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);
  }

  @Test
  public void handleRequest_SimpleSuccess() {
    // set up test scenario
    when(proxyClient.client().getIndex(any(GetIndexRequest.class)))
        .thenReturn(GetIndexResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .description("This is a description of the index.")
            .displayName("IndexName")
            .status(IndexStatus.ACTIVE)
            .indexStatistics(software.amazon.awssdk.services.qbusiness.model.IndexStatistics.builder()
                .textDocumentStatistics(software.amazon.awssdk.services.qbusiness.model.TextDocumentStatistics.builder()
                    .indexedTextBytes(1000L)
                    .indexedTextDocumentCount(1)
                    .build())
                .build())
            .documentAttributeConfigurations(List.of(software.amazon.awssdk.services.qbusiness.model.DocumentAttributeConfiguration.builder()
                .name("DocumentAttributeName")
                .search(Status.ENABLED.toString())
                .type(AttributeType.DATE.toString())
                .build()))
            .capacityConfiguration(software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
                .units(10)
                .build())
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
    verify(sdkClient).getIndex(any(GetIndexRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(responseProgress.getResourceModels()).isNull();
    assertThat(responseProgress.getMessage()).isNull();
    assertThat(responseProgress.getErrorCode()).isNull();
    ResourceModel resultModel = responseProgress.getResourceModel();
    assertThat(resultModel.getDisplayName()).isEqualTo("IndexName");
    assertThat(resultModel.getApplicationId()).isEqualTo(APP_ID);
    assertThat(resultModel.getIndexId()).isEqualTo(INDEX_ID);
    assertThat(resultModel.getCreatedAt()).isEqualTo("2023-10-20T18:02:15Z");
    assertThat(resultModel.getUpdatedAt()).isEqualTo("2023-10-20T22:02:15Z");
    assertThat(resultModel.getDescription()).isEqualTo("This is a description of the index.");
    assertThat(resultModel.getStatus()).isEqualTo(IndexStatus.ACTIVE.toString());
    assertThat(resultModel.getCapacityConfiguration().getUnits()).isEqualTo(10D);
    assertThat(resultModel.getIndexStatistics().getTextDocumentStatistics().getIndexedTextDocumentCount()).isEqualTo(1);
    assertThat(resultModel.getIndexStatistics().getTextDocumentStatistics().getIndexedTextBytes()).isEqualTo(1000L);
    assertThat(resultModel.getDocumentAttributeConfigurations().size()).isEqualTo(1);
    assertThat(resultModel.getDocumentAttributeConfigurations().get(0).getName()).isEqualTo("DocumentAttributeName");
    assertThat(resultModel.getDocumentAttributeConfigurations().get(0).getSearch()).isEqualTo(Status.ENABLED.toString());
    assertThat(resultModel.getDocumentAttributeConfigurations().get(0).getType()).isEqualTo(AttributeType.DATE.toString());

    var tags = resultModel.getTags().stream().map(tag -> Map.entry(tag.getKey(), tag.getValue())).toList();
    assertThat(tags).isEqualTo(List.of(
        Map.entry("Category", "Chat Stuff")
    ));
  }

  @Test
  public void handleRequest_SimpleSuccess_withMissingProperties() {
    // set up test scenario
    when(proxyClient.client().getIndex(any(GetIndexRequest.class)))
        .thenReturn(GetIndexResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .description("This is a description of the index.")
            .displayName("IndexName")
            .status(IndexStatus.ACTIVE)
            .indexStatistics(software.amazon.awssdk.services.qbusiness.model.IndexStatistics.builder().build())
            .build());

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify result
    verify(sdkClient).getIndex(any(GetIndexRequest.class));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    assertThat(responseProgress).isNotNull();
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.SUCCESS);
    assertThat(responseProgress.getResourceModels()).isNull();
    assertThat(responseProgress.getMessage()).isNull();
    assertThat(responseProgress.getErrorCode()).isNull();

    ResourceModel resultModel = responseProgress.getResourceModel();
    assertThat(resultModel.getIndexStatistics().getTextDocumentStatistics()).isNull();
    assertThat(resultModel.getDocumentAttributeConfigurations()).isEmpty();
    assertThat(resultModel.getCapacityConfiguration()).isNull();
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
    when(proxyClient.client().getIndex(any(GetIndexRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getIndex(any(GetIndexRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }

  @ParameterizedTest
  @MethodSource("serviceErrorAndExpectedCfnCode")
  public void testThatItReturnsExpectedErrorCodeWhenListTagsForResourceFails(
      QBusinessException serviceError,
      HandlerErrorCode cfnErrorCode) {
    // set up test scenario
    when(proxyClient.client().getIndex(any(GetIndexRequest.class)))
        .thenReturn(GetIndexResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .createdAt(Instant.ofEpochMilli(1697824935000L))
            .updatedAt(Instant.ofEpochMilli(1697839335000L))
            .description("This is a description of the index.")
            .displayName("IndexName")
            .status(IndexStatus.ACTIVE)
            .indexStatistics(software.amazon.awssdk.services.qbusiness.model.IndexStatistics.builder()
                .textDocumentStatistics(software.amazon.awssdk.services.qbusiness.model.TextDocumentStatistics.builder()
                    .indexedTextBytes(1000L)
                    .indexedTextDocumentCount(1)
                    .build())
                .build())
            .documentAttributeConfigurations(List.of(software.amazon.awssdk.services.qbusiness.model.DocumentAttributeConfiguration.builder()
                .name("DocumentAttributeName")
                .search(Status.ENABLED.toString())
                .type(AttributeType.DATE.toString())
                .build()))
            .capacityConfiguration(software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
                .units(10)
                .build())
            .build());

    when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenThrow(serviceError);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> responseProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(responseProgress.getStatus()).isEqualTo(OperationStatus.FAILED);
    verify(sdkClient).getIndex(any(GetIndexRequest.class));
    assertThat(responseProgress.getErrorCode()).isEqualTo(cfnErrorCode);
    assertThat(responseProgress.getResourceModels()).isNull();
  }
}
