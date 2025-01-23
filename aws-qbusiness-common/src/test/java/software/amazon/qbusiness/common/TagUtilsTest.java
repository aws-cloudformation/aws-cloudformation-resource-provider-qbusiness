package software.amazon.qbusiness.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.StdCallbackContext;

class TagUtilsTest {

  @NoArgsConstructor
  private static class VoidCallBack extends StdCallbackContext {
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
  private static class TestResourceModel {
    @JsonProperty("Tags") List<Tag> tags;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
  private static class Tag {
    @JsonProperty("Key")
    private String key;
    @JsonProperty("Value")
    private String value;
  }

  private static final Credentials MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");

  private TestResourceModel resourceModel;
  private Map<String, String> desiredResourceTags;
  private Map<String, String> desiredSysTags;
  private ResourceHandlerRequest<TestResourceModel> testHandlerRequest;

  private software.amazon.awssdk.services.qbusiness.model.Tag sdkTagResource;
  private software.amazon.awssdk.services.qbusiness.model.Tag sdkTagSystemTag;
  private software.amazon.awssdk.services.qbusiness.model.Tag sdkTagModel;

  @Mock
  private Logger mockLogger;
  @Mock
  private LoggerProxy mockLoggerProxy;
  @Mock
  private QBusinessClient mockQClient;
  @Mock
  private ProgressEvent<TestResourceModel, VoidCallBack> mockProgressEvent;

  private AmazonWebServicesClientProxy proxy;

  private ProxyClient<QBusinessClient> proxyClient;

  private AutoCloseable testAutoCloseable;

  @BeforeEach
  void setUp() {
    testAutoCloseable = MockitoAnnotations.openMocks(this);
    resourceModel = TestResourceModel.builder().tags(List.of(
        Tag.builder().key("tagA").value("valueA").build()
    )).build();
    desiredResourceTags = Map.of("stackTagA", "stackValueB");
    desiredSysTags = Map.of("aws:cloudformation:stack-id", "superstack");
    testHandlerRequest = ResourceHandlerRequest.<TestResourceModel>builder()
        .desiredResourceState(resourceModel)
        .desiredResourceTags(desiredResourceTags)
        .systemTags(desiredSysTags)
        .build();

    sdkTagModel = software.amazon.awssdk.services.qbusiness.model.Tag.builder()
        .key("tagA").value("valueA").build();
    sdkTagResource = software.amazon.awssdk.services.qbusiness.model.Tag.builder()
        .key("stackTagA").value("stackValueB").build();

    sdkTagSystemTag = software.amazon.awssdk.services.qbusiness.model.Tag.builder()
        .key("aws:cloudformation:stack-id").value("superstack").build();

    proxy = new AmazonWebServicesClientProxy(mockLoggerProxy, MOCK_CREDENTIALS, () -> Duration.ofMinutes(5).toMillis());

    proxyClient = new ProxyClient<>() {
      @Override public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT injectCredentialsAndInvokeV2(
          RequestT requestT,
          Function<RequestT, ResponseT> function
      ) {
        return proxy.injectCredentialsAndInvokeV2(requestT, function);
      }

      @Override public QBusinessClient client() {
        return mockQClient;
      }
    };

    //    when(mockProxyClient.client()).thenReturn(mockQClient);
    when(mockProgressEvent.getResourceModel()).thenReturn(
        TestResourceModel.builder().build()
    );
  }

  @AfterEach
  void tearDown() throws Exception {
    verifyNoMoreInteractions(mockQClient);
    testAutoCloseable.close();
  }

  @Test
  void testItMergesTagsIntoQBusinessTags_HappyCase() {
    var result = TagUtils.mergeCreateHandlerTagsToSdkTags(testHandlerRequest, resourceModel);
    assertThat(result).containsExactlyInAnyOrder(sdkTagModel, sdkTagResource, sdkTagSystemTag);
  }

  @Test
  void testItMergesTagsIntoQBusinessTags_NullModelTags() {
    var result = TagUtils.mergeCreateHandlerTagsToSdkTags(testHandlerRequest,
        TestResourceModel.builder().build()
    );
    assertThat(result).containsExactlyInAnyOrder(sdkTagResource, sdkTagSystemTag);
  }

  @Test
  void testItMergesTagsIntoQBusinessTags_NullResourceTags() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .desiredResourceTags(null).build();
    var result = TagUtils.mergeCreateHandlerTagsToSdkTags(testHandlerRequest, resourceModel);
    assertThat(result).containsExactlyInAnyOrder(sdkTagModel, sdkTagSystemTag);
  }

  @Test
  void testItMergesTagsIntoQBusinessTags_NullSystemTags() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .systemTags(null).build();
    var result = TagUtils.mergeCreateHandlerTagsToSdkTags(testHandlerRequest, resourceModel);
    assertThat(result).containsExactlyInAnyOrder(sdkTagModel, sdkTagResource);
  }

  @Test
  void testItThrowsIfUnexpectedModel() {
    Object veryBadModel = Map.of(
        "Tags", Map.of("Hello", "World")
    );

    var testHandlerRequest = ResourceHandlerRequest.builder()
        .desiredResourceState(resourceModel)
        .desiredResourceTags(desiredResourceTags)
        .systemTags(desiredSysTags)
        .build();
    assertThatThrownBy(() -> TagUtils.mergeCreateHandlerTagsToSdkTags(testHandlerRequest, veryBadModel))
        .isInstanceOf(CfnGeneralServiceException.class);
  }

  @Test
  void testItUpdatesRemovesAndAddNewTags() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .previousResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build(),
                Tag.builder().key("tagB").value("valueB").build(), // gets removed
                Tag.builder().key("tagC").value("valueC").build()
            )).build()
        )
        .previousResourceTags(Map.of(
            "stackTagA", "stackValueA", // gets removed
            "stackTagB", "stackValueB",
            "stackTagC", "stackValueC"
        ))
        .previousSystemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack",
            "aws:cloudformation:lion-sin-pride", "AtHighNoon",
            "aws:cloudformation:full", "counter" // gets removed
        ))

        .desiredResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("newValueA").build(), // changing
                Tag.builder().key("tagC").value("valueC").build(),
                Tag.builder().key("tagD").value("valueD").build() // new tag
            )).build()
        )
        .desiredResourceTags(Map.of(
            "stackTagB", "newStackValueB", // changing
            "stackTagC", "stackValueC", // staying same
            "stackTagJJ", "stackValueJJJ" // new
        ))
        .systemTags(Map.of(
            "aws:cloudformation:stack-id", "TheSeven", //changing tag
            "aws:cloudformation:sloth", "Romane", // new tag
            "aws:cloudformation:lion-sin-pride", "AtHighNoon" // staying same
        ))
        .build();

    ProgressEvent<TestResourceModel, VoidCallBack> result =
        TagUtils.updateTags("MyType", mockProgressEvent, testHandlerRequest, "thearn", proxyClient, mockLogger);

    assertThat(result).isNotNull();

    var tagRequestCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockQClient).tagResource(tagRequestCaptor.capture());

    var tagRequest = tagRequestCaptor.getValue();
    assertThat(tagRequest.resourceARN()).isEqualTo("thearn");
    assertThat(tagRequest.tags()).containsExactlyInAnyOrder(
        qTag("tagA", "newValueA"),
        qTag("tagD", "valueD"),
        qTag("aws:cloudformation:stack-id", "TheSeven"),
        qTag("aws:cloudformation:sloth", "Romane"),
        qTag("stackTagJJ", "stackValueJJJ"),
        qTag("stackTagB", "newStackValueB")
    );

    var untagRequestCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(mockQClient).untagResource(untagRequestCaptor.capture());

    var untagRequest = untagRequestCaptor.getValue();
    assertThat(untagRequest.resourceARN()).isEqualTo("thearn");
    assertThat(untagRequest.tagKeys()).containsExactlyInAnyOrder(
        "tagB", "stackTagA", "aws:cloudformation:full"
    );
  }

  @Test
  void testItDoesNotCallQIfNoChanges() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .previousResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build()
            )).build()
        )
        .previousResourceTags(Map.of(
            "stackTagA", "stackValueA"
        ))
        .previousSystemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack"
        ))
        .desiredResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build()
            )).build()
        )
        .desiredResourceTags(Map.of(
            "stackTagA", "stackValueA"
        ))
        .systemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack"
        ))
        .build();

    var result = TagUtils.updateTags("MyType", mockProgressEvent, testHandlerRequest, "thearn", proxyClient, mockLogger);
    assertThat(result).isNotNull();
  }

  @Test
  void testItCallsUntagToRemoveButNotCallTagSinceNoneUpdate() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .previousResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build(),
                Tag.builder().key("tagB").value("valueB").build()
            )).build()
        )
        .previousResourceTags(Map.of(
            "stackTagA", "stackValueA",
            "stackTagB", "stackValueB"
        ))
        .previousSystemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack",
            "aws:cloudformation:the-great", "powers"
        ))
        .desiredResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build()
            )).build()
        )
        .desiredResourceTags(Map.of(
            "stackTagA", "stackValueA"
        ))
        .systemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack"
        ))
        .build();

    var result = TagUtils.updateTags("MyType", mockProgressEvent, testHandlerRequest, "thearn", proxyClient, mockLogger);
    assertThat(result).isNotNull();

    var unTagRequestCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(mockQClient).untagResource(unTagRequestCaptor.capture());

    var untagResourceRequest = unTagRequestCaptor.getValue();
    assertThat(untagResourceRequest.tagKeys()).containsExactlyInAnyOrder(
        "tagB", "stackTagB", "aws:cloudformation:the-great"
    );

    verify(mockQClient, never()).tagResource(any(TagResourceRequest.class));
  }

  @Test
  void testItCallsTagForUpdatesButNotUntagSinceNoRemovals() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .previousResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build(),
                Tag.builder().key("tagB").value("valueB").build()
            )).build()
        )
        .previousResourceTags(Map.of(
            "stackTagA", "stackValueA",
            "stackTagB", "stackValueB"
        ))
        .previousSystemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack",
            "aws:cloudformation:the-great", "powers"
        ))

        .desiredResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build(),
                Tag.builder().key("tagB").value("NewValueTagB").build()
            )).build()
        )
        .desiredResourceTags(Map.of(
            "stackTagA", "stackValueA",
            "stackTagB", "NewStackValueB"
        ))
        .systemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack",
            "aws:cloudformation:the-great", "noMore"
        ))
        .build();

    var result = TagUtils.updateTags("MyType", mockProgressEvent, testHandlerRequest, "thearn", proxyClient, mockLogger);
    assertThat(result).isNotNull();

    var tagRequestCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockQClient).tagResource(tagRequestCaptor.capture());
    verify(mockQClient, never()).untagResource(any(UntagResourceRequest.class));

    var tagRequest = tagRequestCaptor.getValue();
    assertThat(tagRequest.tags()).containsExactlyInAnyOrder(
        qTag("tagB", "NewValueTagB"),
        qTag("stackTagB", "NewStackValueB"),
        qTag("aws:cloudformation:the-great", "noMore")
    );
  }

  @Test
  void testItReturnsFailedProgressEventIfAnExceptionIsThrownByQ() {
    testHandlerRequest = testHandlerRequest.toBuilder()
        .previousResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("valueA").build()
            )).build()
        )
        .previousResourceTags(Map.of(
            "stackTagA", "stackValueA"
        ))
        .previousSystemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack"
        ))
        .desiredResourceState(
            TestResourceModel.builder().tags(List.of(
                Tag.builder().key("tagA").value("newValue").build()
            )).build()
        )
        .desiredResourceTags(Map.of(
            "stackTagA", "stackValueA"
        ))
        .systemTags(Map.of(
            "aws:cloudformation:stack-id", "thatStackedStack"
        ))
        .build();
    when(mockQClient.tagResource(any(TagResourceRequest.class))).thenThrow(
        AccessDeniedException.builder().message("no, you cannot").build()
    );

    var result = TagUtils.updateTags("MyType", mockProgressEvent, testHandlerRequest, "thearn", proxyClient, mockLogger);
    assertThat(result).isNotNull();

    var tagRequestCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(mockQClient).tagResource(tagRequestCaptor.capture());
    verify(mockQClient, never()).untagResource(any(UntagResourceRequest.class));

    var tagRequest = tagRequestCaptor.getValue();
    assertThat(tagRequest.tags()).containsExactlyInAnyOrder(
        qTag("tagA", "newValue")
    );

    assertThat(result.isFailed()).isTrue();
    assertThat(result.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
    assertThat(result.getMessage()).isEqualTo("no, you cannot");
  }

  private software.amazon.awssdk.services.qbusiness.model.Tag qTag(String key, String val) {
    return software.amazon.awssdk.services.qbusiness.model.Tag.builder()
        .key(key)
        .value(val)
        .build();
  }

}
