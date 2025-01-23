package software.amazon.qbusiness.dataaccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.TagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {
    private static final String APPLICATION_ID = "ApplicationId";
    private static final String DATAACCESSOR_ID = "DataAccessorId";
    private static final String DATAACCESSOR_NAME = "DataAccessorName";
    private static final String DATAACCESSOR_ARN = "DataAccessorArn";
    private static final String IDC_APPLICATION_ARN = "IdcApplicationArn";
    private static final String PRINCIPAL = "principal";
    private static final Long CREATED_TIME = 1697824935000L;
    private static final Long UPDATED_TIME = 1697839335000L;
    private static final String CLIENT_TOKEN = "client-token";
    private static final String AWS_PARTITION = "aws";
    private static final String ACCOUNT_ID = "123456789012";
    private static final String REGION = "us-west-2";
    @Mock
    private AmazonWebServicesClientProxy proxy;
    @Mock
    private ProxyClient<QBusinessClient> proxyClient;
    @Mock
    QBusinessClient qBusinessClient;
    private UpdateHandler underTest;
    private ResourceModel model;
    private ResourceModel updatedModel;
    private ResourceHandlerRequest<ResourceModel> request;
    private final TagHelper tagHelper = spy(new TagHelper());
    private software.amazon.awssdk.services.qbusiness.model.ActionConfiguration actionConfiguration;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        qBusinessClient = mock(QBusinessClient.class);
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        underTest = new UpdateHandler(tagHelper);

        model = ResourceModel.builder()
            .applicationId(APPLICATION_ID)
            .dataAccessorId(DATAACCESSOR_ID)
            .displayName(DATAACCESSOR_NAME)
            .dataAccessorArn(DATAACCESSOR_ARN)
            .idcApplicationArn(IDC_APPLICATION_ARN)
            .principal(PRINCIPAL)
            .actionConfigurations(Collections.singletonList(ActionConfiguration.builder().action("test-action").build()))
            .createdAt(Instant.ofEpochMilli(CREATED_TIME).toString())
            .updatedAt(Instant.ofEpochMilli(UPDATED_TIME).toString())
            .tags(List.of(Tag.builder().key("Tag 1").value("value1").build(), Tag.builder().key("Tag 3").value("value3").build()))
            .build();

        actionConfiguration = software.amazon.awssdk.services.qbusiness.model.ActionConfiguration.builder()
            .action("test-action")
            .build();

        updatedModel = ResourceModel.builder()
            .applicationId(APPLICATION_ID)
            .dataAccessorId(DATAACCESSOR_ID)
            .displayName(DATAACCESSOR_NAME)
            .dataAccessorArn(DATAACCESSOR_ARN)
            .idcApplicationArn(IDC_APPLICATION_ARN)
            .principal(PRINCIPAL)
            .actionConfigurations(Collections.singletonList(ActionConfiguration.builder()
                .action("test-action")
                .filterConfiguration(ActionFilterConfiguration.builder()
                    .documentAttributeFilter(AttributeFilter.builder()
                        .equalsTo(DocumentAttribute.builder()
                            .name("_data_source_id")
                            .value(DocumentAttributeValue.builder().stringValue("d86a7179-0747-4883-89cb-87b039d6ddd2").build())
                            .build())
                        .build())
                    .build())
                .build()))
            .createdAt(Instant.ofEpochMilli(CREATED_TIME).toString())
            .updatedAt(Instant.ofEpochMilli(UPDATED_TIME).toString())
            .tags(List.of(Tag.builder().key("Tag 1").value("value1").build(), Tag.builder().key("Tag 2").value("value2").build()))
            .build();

        request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(updatedModel)
            .previousResourceState(model)
            .awsPartition(AWS_PARTITION)
            .region(REGION)
            .awsAccountId(ACCOUNT_ID)
            .clientRequestToken(CLIENT_TOKEN)
            .build();
        when(proxyClient.client().updateDataAccessor(any(UpdateDataAccessorRequest.class)))
            .thenReturn(UpdateDataAccessorResponse.builder()
                .build());

        when(qBusinessClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
            .thenReturn(ListTagsForResourceResponse.builder().tags(List.of()).build());
    }

    @AfterEach
    public void tear_down() {
        verify(qBusinessClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(qBusinessClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        when(proxyClient.client().getDataAccessor(any(GetDataAccessorRequest.class)))
            .thenReturn(GetDataAccessorResponse.builder()
                .applicationId(APPLICATION_ID)
                .dataAccessorId(DATAACCESSOR_ID)
                .displayName(DATAACCESSOR_NAME)
                .dataAccessorArn(DATAACCESSOR_ARN)
                .idcApplicationArn(IDC_APPLICATION_ARN)
                .principal(PRINCIPAL)
                .actionConfigurations(Collections.singletonList(actionConfiguration))
                .createdAt(Instant.ofEpochMilli(CREATED_TIME))
                .updatedAt(Instant.ofEpochMilli(UPDATED_TIME))
                .build());
        // 1 new tag addition detected
        when(qBusinessClient.tagResource(any(TagResourceRequest.class)))
            .thenReturn(TagResourceResponse.builder().build());
        // 1 old tag removal detected
        when(qBusinessClient.untagResource(any(UntagResourceRequest.class)))
            .thenReturn(UntagResourceResponse.builder().build());
        final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(
            proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel().getApplicationId()).isEqualTo(request.getDesiredResourceState().getApplicationId());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
