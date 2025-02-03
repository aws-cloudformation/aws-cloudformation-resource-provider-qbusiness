package software.amazon.qbusiness.datasource;

import static org.assertj.core.api.Assertions.assertThat;
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
import software.amazon.awssdk.services.qbusiness.model.AttributeValueOperator;
import software.amazon.awssdk.services.qbusiness.model.DataSourceStatus;
import software.amazon.awssdk.services.qbusiness.model.DocumentContentOperator;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ImageExtractionStatus;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataSourceResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class UpdateHandlerTest extends AbstractTestBase {

  private static final String APP_ID = "2a18a774-ddd5-487f-a223-0dbe8e2794a9";
  private static final String INDEX_ID = "6550e001-86a0-400f-b3f2-d9e69a474c7f";
  private static final String DATA_SOURCE_ID = "704419db-fbb0-4091-9211-cdf2d45ee4d2";

  private static final String ACCOUNT_ID = "111333444555";

  private AmazonWebServicesClientProxy proxy;

  private ProxyClient<QBusinessClient> proxyClient;

  @Mock
  private QBusinessClient sdkClient;

  private AutoCloseable testMocks;

  private UpdateHandler underTest;

  private ResourceHandlerRequest<ResourceModel> testRequest;
  private ResourceModel previousModel;
  private ResourceModel updateModel;

  @BeforeEach
  public void setup() {
    testMocks = MockitoAnnotations.openMocks(this);
    proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
    sdkClient = mock(QBusinessClient.class);
    proxyClient = MOCK_PROXY(proxy, sdkClient);

    underTest = new UpdateHandler(Constant.of()
        .delay(Duration.ofSeconds(5))
        .timeout(Duration.ofSeconds(45))
        .build()
    );

    previousModel = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
        .syncSchedule("0 11 * * 4")
        .displayName("oldname")
        .description("old desc")
        .roleArn("old role")
        .configuration(
            Map.of(
                "depth", 10,
                "thing", Map.of(
                    "a", 5
                )
            )
        )
        .tags(List.of(
            Tag.builder().key("remain").value("thesame").build(),
            Tag.builder().key("toremove").value("nolongerthere").build(),
            Tag.builder().key("iwillchange").value("oldvalue").build()
        ))
        .build();

    updateModel = ResourceModel.builder()
        .applicationId(APP_ID)
        .indexId(INDEX_ID)
        .dataSourceId(DATA_SOURCE_ID)
        .syncSchedule("0 12 * * 1")
        .displayName("new-name")
        .description("new desc")
        .roleArn("new role")
        .configuration(
            Map.of(
                "depth", 10,
                "thing", Map.of(
                    "a", 5
                )
            )
        )
        .tags(List.of(
            Tag.builder().key("remain").value("thesame").build(),
            Tag.builder().key("iwillchange").value("nowanewvalue").build(),
            Tag.builder().key("iamnew").value("overhere").build()
        ))
        .documentEnrichmentConfiguration(DocumentEnrichmentConfiguration.builder()
            .inlineConfigurations(List.of(InlineDocumentEnrichmentConfiguration.builder()
                .documentContentOperator(DocumentContentOperator.DELETE.toString())
                .target(DocumentAttributeTarget.builder()
                    .attributeValueOperator(AttributeValueOperator.DELETE.toString())
                    .key("Keykey")
                    .value(DocumentAttributeValue.builder().longValue(50D).build())
                    .build()
                )
                .build()
            ))
            .build()
        )
        .mediaExtractionConfiguration(MediaExtractionConfiguration.builder()
            .imageExtractionConfiguration(ImageExtractionConfiguration.builder()
                .imageExtractionStatus("ENABLED").build())
            .build())
        .build();

    testRequest = ResourceHandlerRequest.<ResourceModel>builder()
        .awsAccountId(ACCOUNT_ID)
        .awsPartition("aws")
        .region("us-east-1")
        .stackId("Stack1")
        .systemTags(Map.of(
            "aws::cloudformation::created", "onthisday",
            "aws::cloudformation::nowupdated", "thismoment"
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
        .desiredResourceState(updateModel)
        .build();

    when(sdkClient.updateDataSource(any(UpdateDataSourceRequest.class)))
        .thenReturn(UpdateDataSourceResponse.builder().build());
    when(sdkClient.tagResource(any(TagResourceRequest.class)))
        .thenReturn(TagResourceResponse.builder().build());
    when(sdkClient.untagResource(any(UntagResourceRequest.class)))
        .thenReturn(UntagResourceResponse.builder().build());
    when(sdkClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
        .thenReturn(ListTagsForResourceResponse.builder().tags(List.of()).build());
    when(sdkClient.getDataSource(any(GetDataSourceRequest.class))).thenReturn(
        GetDataSourceResponse.builder()
            .applicationId(APP_ID)
            .indexId(INDEX_ID)
            .dataSourceId(DATA_SOURCE_ID)
            .status(DataSourceStatus.ACTIVE)
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
    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();

    var updateReqCaptor = ArgumentCaptor.forClass(UpdateDataSourceRequest.class);
    var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(sdkClient).updateDataSource(updateReqCaptor.capture());
    verify(sdkClient).tagResource(tagReqCaptor.capture());
    verify(sdkClient).untagResource(untagReqCaptor.capture());
    verify(sdkClient, times(2)).getDataSource(argThat(getAppMatcher()));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var updateReqArgument = updateReqCaptor.getValue();
    assertThat(updateReqArgument.syncSchedule()).isEqualTo(updateModel.getSyncSchedule());
    assertThat(updateReqArgument.displayName()).isEqualTo(updateModel.getDisplayName());
    assertThat(updateReqArgument.description()).isEqualTo(updateModel.getDescription());
    assertThat(updateReqArgument.roleArn()).isEqualTo(updateModel.getRoleArn());

    var updateInlineConf = updateReqArgument.documentEnrichmentConfiguration().inlineConfigurations().get(0);
    assertThat(updateInlineConf.documentContentOperator()).isEqualTo(DocumentContentOperator.DELETE);

    var updateImageExtractionConfig = updateReqArgument.mediaExtractionConfiguration()
        .imageExtractionConfiguration();
    assertThat(updateImageExtractionConfig.imageExtractionStatus()).isEqualTo(ImageExtractionStatus.ENABLED);

    var tagResourceRequest = tagReqCaptor.getValue();
    Map<String, String> tagsInTagResourceReq = tagResourceRequest.tags().stream()
        .collect(Collectors.toMap(
            software.amazon.awssdk.services.qbusiness.model.Tag::key,
            software.amazon.awssdk.services.qbusiness.model.Tag::value
        ));
    assertThat(tagsInTagResourceReq).containsOnly(
        Map.entry("iwillchange", "nowanewvalue"),
        Map.entry("iamnew", "overhere"),
        Map.entry("stackchange", "whatwhenwhere"),
        Map.entry("stacknewaddition", "newvalue"),
        Map.entry("aws::cloudformation::nowupdated", "thismoment")
    );

    var untagResourceReq = untagReqCaptor.getValue();
    assertThat(untagResourceReq.tagKeys()).containsOnlyOnceElementsOf(List.of(
        "toremove",
        "stack-i-remove"
    ));
  }

  @Test
  public void testThatItDoesntTagAndUnTag() {
    // set up scenario
    previousModel.setTags(List.of(
        Tag.builder().key("remain").value("thesame").build()
    ));
    updateModel.setTags(List.of(
        Tag.builder().key("remain").value("thesame").build()
    ));

    testRequest.setPreviousResourceTags(Map.of(
        "stacksame", "value"
    ));
    testRequest.setDesiredResourceTags(Map.of(
        "stacksame", "value"
    ));

    testRequest.setSystemTags(Map.of(
        "aws::cloudformation::created", "onthisday"
    ));
    testRequest.setPreviousSystemTags(Map.of(
        "aws::cloudformation::created", "onthisday"
    ));

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(sdkClient).updateDataSource(any(UpdateDataSourceRequest.class));
    verify(sdkClient, times(2)).getDataSource(argThat(getAppMatcher()));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
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
    updateModel.setTags(modelTags);
    testRequest.setPreviousResourceTags(reqTags);
    testRequest.setDesiredResourceTags(reqTags);
    testRequest.setPreviousSystemTags(sysTags);
    testRequest.setSystemTags(sysTags);

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(sdkClient).updateDataSource(any(UpdateDataSourceRequest.class));
    verify(sdkClient, times(2)).getDataSource(argThat(getAppMatcher()));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));
  }

  @Test
  public void testThatItCallsTagButSkipsCallingUntag() {
    // set up
    testRequest.setPreviousResourceTags(Map.of(
        "stacksame", "value"
    ));
    testRequest.setDesiredResourceTags(Map.of(
        "stacksame", "newValue"
    ));

    previousModel.setTags(List.of(
        Tag.builder().key("datTag").value("valuea").build()
    ));
    updateModel.setTags(List.of(
        Tag.builder().key("datTag").value("valueb").build()
    ));

    testRequest.setPreviousSystemTags(Map.of(
        "aws::cloudformation::created", "onthisday"
    ));

    testRequest.setSystemTags(Map.of(
        "aws::cloudformation::created", "onthisday-new-day"
    ));

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(sdkClient).updateDataSource(any(UpdateDataSourceRequest.class));

    verify(sdkClient, times(2)).getDataSource(argThat(getAppMatcher()));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var tagReqCaptor = ArgumentCaptor.forClass(TagResourceRequest.class);
    verify(sdkClient).tagResource(tagReqCaptor.capture());

    var tagRequest = tagReqCaptor.getValue();
    Map<String, String> tagsAddedInReq = tagRequest.tags().stream()
        .collect(Collectors.toMap(tag -> tag.key(), tag -> tag.value()));

    assertThat(tagsAddedInReq.entrySet()).isEqualTo(Set.of(
        Map.entry("stacksame", "newValue"),
        Map.entry("datTag", "valueb"),
        Map.entry("aws::cloudformation::created", "onthisday-new-day")
    ));
  }

  @Test
  public void testThatItCallsUnTagButSkipsCallingTag() {
    // set up scenario
    previousModel.setTags(List.of(
        Tag.builder().key("remain").value("thesame").build(),
        Tag.builder().key("toBeRemove").value("theValue").build()
    ));
    updateModel.setTags(List.of(
        Tag.builder().key("remain").value("thesame").build()
    ));

    testRequest.setPreviousResourceTags(Map.of(
        "stacksame", "value"
    ));
    testRequest.setDesiredResourceTags(Map.of(
        "stacksame", "value"
    ));

    testRequest.setPreviousSystemTags(Map.of(
        "aws::cloudformation::created", "onthisday",
        "aws::cloudformation::update", "otherthere"
    ));

    testRequest.setSystemTags(Map.of(
        "aws::cloudformation::created", "onthisday"
    ));

    // call method under test
    final ProgressEvent<ResourceModel, CallbackContext> resultProgress = underTest.handleRequest(
        proxy, testRequest, new CallbackContext(), proxyClient, logger
    );

    // verify
    assertThat(resultProgress).isNotNull();
    assertThat(resultProgress.isSuccess()).isTrue();
    verify(sdkClient).updateDataSource(any(UpdateDataSourceRequest.class));
    verify(sdkClient, times(2)).getDataSource(argThat(getAppMatcher()));
    verify(sdkClient).listTagsForResource(any(ListTagsForResourceRequest.class));

    var untagReqCaptor = ArgumentCaptor.forClass(UntagResourceRequest.class);
    verify(sdkClient).untagResource(untagReqCaptor.capture());

    verify(sdkClient, times(0)).tagResource(any(TagResourceRequest.class));

    var untagReq = untagReqCaptor.getValue();
    assertThat(untagReq.tagKeys()).isEqualTo(List.of("toBeRemove", "aws::cloudformation::update"));
  }

  private ArgumentMatcher<GetDataSourceRequest> getAppMatcher() {
    return t -> t.applicationId().equals(APP_ID) && t.indexId().equals(INDEX_ID) && t.dataSourceId().equals(DATA_SOURCE_ID);
  }
}
