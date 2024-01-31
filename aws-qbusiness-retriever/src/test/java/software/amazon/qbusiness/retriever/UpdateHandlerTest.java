package software.amazon.qbusiness.retriever;

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
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverResponse;
import software.amazon.awssdk.services.qbusiness.model.KendraIndexConfiguration;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.RetrieverConfiguration;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateRetrieverResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class UpdateHandlerTest extends AbstractTestBase {
  private static final String APP_ID = "ApplicationId";
  private static final String RETRIEVER_ID = "RetrieverId";
  private static final String RETRIEVER_NAME = "RetrieverName";
  private static final String INDEX_ID = "IndexId";
  private static final String INDEX_ID_NEW = "IndexIdNew";
  private static final String ROLE_ARN = "role-1";
  private static final String CLIENT_TOKEN = "client-token";
  @Mock
  private AmazonWebServicesClientProxy proxy;

  @Mock
  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testMocks;

  private UpdateHandler underTest;
  private ResourceModel previousModel;
  private ResourceModel updatedModel;
  private ResourceHandlerRequest<ResourceModel> request;
  private final TagHelper tagHelper = spy(new TagHelper());


  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    var testBackOff = Constant.of()
        .delay(Duration.ofSeconds(5))
        .timeout(Duration.ofSeconds(45))
        .build();
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);
    this.underTest = new UpdateHandler(testBackOff, tagHelper);

    KendraIndexConfiguration previousKendraIndexConfiguration = KendraIndexConfiguration.builder()
        .indexId(INDEX_ID)
        .build();
    RetrieverConfiguration previousRetrieverConfiguration = RetrieverConfiguration.builder()
        .kendraIndexConfiguration(previousKendraIndexConfiguration)
        .build();

    KendraIndexConfiguration kendraIndexConfiguration = KendraIndexConfiguration.builder()
        .indexId(INDEX_ID_NEW)
        .build();
    RetrieverConfiguration updatedRetrieverConfiguration = RetrieverConfiguration.builder()
        .kendraIndexConfiguration(kendraIndexConfiguration)
        .build();

    previousModel = ResourceModel.builder()
        .applicationId(APP_ID)
        .retrieverId(RETRIEVER_ID)
        .configuration(Translator.fromServiceRetrieverConfiguration(previousRetrieverConfiguration))
        .displayName(RETRIEVER_NAME)
        .roleArn(ROLE_ARN)
        .tags(List.of(
            Tag.builder().key("remain").value("thesame").build(),
            Tag.builder().key("toremove").value("nolongerthere").build(),
            Tag.builder().key("iwillchange").value("oldvalue").build()
        ))
        .build();

    updatedModel = ResourceModel.builder()
        .applicationId(APP_ID)
        .retrieverId(RETRIEVER_ID)
        .configuration(Translator.fromServiceRetrieverConfiguration(updatedRetrieverConfiguration))
        .displayName(RETRIEVER_NAME)
        .roleArn(ROLE_ARN)
        .tags(List.of(
            Tag.builder().key("remain").value("thesame").build(),
            Tag.builder().key("iwillchange").value("nowanewvalue").build(),
            Tag.builder().key("iamnew").value("overhere").build()
        ))
        .build();

    request = ResourceHandlerRequest.<ResourceModel>builder()
        .awsPartition("aws")
        .region("us-west-2")
        .awsAccountId("123412341234")
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
        .previousResourceState(previousModel)
        .desiredResourceState(updatedModel)
        .clientRequestToken(CLIENT_TOKEN)
        .build();

    when(sdkClient.updateRetriever(any(UpdateRetrieverRequest.class)))
        .thenReturn(UpdateRetrieverResponse.builder().build());
    when(sdkClient.getRetriever(any(GetRetrieverRequest.class)))
        .thenReturn(GetRetrieverResponse.builder()
            .applicationId(APP_ID)
            .retrieverId(RETRIEVER_ID)
            .build());
    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenReturn(ListTagsForResourceResponse.builder().tags(List.of()).build());
  }

  @AfterEach
  public void tear_down() throws Exception {
    verify(sdkClient, atLeastOnce()).serviceName();
    verifyNoMoreInteractions(sdkClient);
    testMocks.close();
  }

  @Test
  public void handleRequest_Success() {
    when(sdkClient.tagResource(any(TagResourceRequest.class)))
        .thenReturn(TagResourceResponse.builder().build());
    when(sdkClient.untagResource(any(UntagResourceRequest.class)))
        .thenReturn(UntagResourceResponse.builder().build());
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, request, new CallbackContext(), proxyClient, logger
    );

    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    ArgumentCaptor<UpdateRetrieverRequest> updateRetrieverReqCaptor = ArgumentCaptor.forClass(UpdateRetrieverRequest.class);
    verify(sdkClient).updateRetriever(updateRetrieverReqCaptor.capture());
    var updateRetrieverRequest = updateRetrieverReqCaptor.getValue();
    assertThat(updateRetrieverRequest.applicationId()).isEqualTo(APP_ID);
    assertThat(updateRetrieverRequest.retrieverId()).isEqualTo(RETRIEVER_ID);
    assertThat(updateRetrieverRequest.configuration()).isEqualTo(Translator.toServiceRetrieverConfiguration(updatedModel.getConfiguration()));
    assertThat(updateRetrieverRequest.displayName()).isEqualTo(RETRIEVER_NAME);
    assertThat(updateRetrieverRequest.roleArn()).isEqualTo(ROLE_ARN);

    verify(sdkClient).getRetriever(
        argThat((ArgumentMatcher<GetRetrieverRequest>) t ->
            t.applicationId().equals(APP_ID) && t.retrieverId().equals(RETRIEVER_ID)
        )
    );
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(sdkClient).tagResource(tagReqCaptor.capture());
    verify(sdkClient).untagResource(untagReqCaptor.capture());

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
  public void testThatItDoesntTagAndUnTag() {
    when(sdkClient.tagResource(any(TagResourceRequest.class)))
        .thenReturn(TagResourceResponse.builder().build());
    request.setPreviousResourceTags(Map.of(
        "stacksame", "value"
    ));
    request.setDesiredResourceTags(Map.of(
        "stacksame", "newValue"
    ));
    previousModel.setTags(List.of(
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
    verify(sdkClient).updateRetriever(any(UpdateRetrieverRequest.class));

    verify(sdkClient).getRetriever(
        argThat((ArgumentMatcher<GetRetrieverRequest>) t ->
          t.applicationId().equals(APP_ID) && t.retrieverId().equals(RETRIEVER_ID)
        )
    );
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(sdkClient).tagResource(tagReqCaptor.capture());

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
    when(sdkClient.untagResource(any(UntagResourceRequest.class)))
        .thenReturn(UntagResourceResponse.builder().build());
    previousModel.setTags(List.of(
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
    verify(sdkClient).updateRetriever(any(UpdateRetrieverRequest.class));
    verify(sdkClient).getRetriever(
        argThat((ArgumentMatcher<GetRetrieverRequest>) t ->
            t.applicationId().equals(APP_ID) && t.retrieverId().equals(RETRIEVER_ID)
        )
    );
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(sdkClient).untagResource(untagReqCaptor.capture());

    verify(sdkClient, times(0)).tagResource(any(TagResourceRequest.class));

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
    // set up test scenario
    previousModel.setTags(modelTags);
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
    verify(sdkClient).updateRetriever(any(UpdateRetrieverRequest.class));
    verify(sdkClient).getRetriever(
        argThat((ArgumentMatcher<GetRetrieverRequest>) t ->
            t.applicationId().equals(APP_ID) && t.retrieverId().equals(RETRIEVER_ID)
        )
    );
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
    verify(tagHelper).shouldUpdateTags(any());
  }
}
