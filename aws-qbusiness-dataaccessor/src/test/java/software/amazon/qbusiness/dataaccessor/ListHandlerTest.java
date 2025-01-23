package software.amazon.qbusiness.dataaccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.DataAccessor;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsRequest;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase{
    private static final String TEST_NEXT_TOKEN = "this-is-next-token";
    private static final String APP_ID = "63451660-1596-4f1a-a3c8-e5f4b33d9fe5";
    @Mock
    private AmazonWebServicesClientProxy proxy;
    @Mock
    private ProxyClient<QBusinessClient> proxyClient;
    @Mock
    QBusinessClient qBusinessClient;
    private ListHandler handler;
    private ResourceModel model;
    private ResourceHandlerRequest<ResourceModel> testRequest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        qBusinessClient = mock(QBusinessClient.class);
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        handler = new ListHandler();
        model = ResourceModel.builder()
            .applicationId(APP_ID)
            .build();
        testRequest = ResourceHandlerRequest.<ResourceModel>builder()
            .nextToken(TEST_NEXT_TOKEN)
            .desiredResourceState(model)
            .awsAccountId("123456")
            .awsPartition("aws")
            .region("us-east-1")
            .stackId("Stack1")
            .build();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        // set up scenario
        List<String> ids = List.of(
            "a98163cb-407b-492c-85d7-a96ebc514eac",
            "db6a3cc2-3de5-4ede-b802-80f107d63ad8",
            "25e148e0-777d-4f30-b523-1f895c36cf55"
        );
        var listDataAccessorsSummaries = ids.stream()
            .map(id -> DataAccessor.builder()
                .dataAccessorId(id)
                .build()
            ).toList();
        when(qBusinessClient.listDataAccessors(any(ListDataAccessorsRequest.class)))
            .thenReturn(ListDataAccessorsResponse.builder()
                .dataAccessors(listDataAccessorsSummaries)
                .build()
            );

        final ProgressEvent<ResourceModel, CallbackContext> response =
            handler.handleRequest(proxy, testRequest, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
