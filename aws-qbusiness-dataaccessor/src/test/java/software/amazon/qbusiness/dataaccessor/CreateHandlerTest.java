package software.amazon.qbusiness.dataaccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorResponse;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {
    private static final String APPLICATION_ID = "ApplicationId";
    private static final String DATAACCESSOR_ID = "DataAccessorId";
    private static final String DATAACCESSOR_NAME = "DataAccessorName";
    private static final String DATAACCESSOR_ARN = "DataAccessorArn";
    private static final String IDC_APPLICATION_ARN = "IdcApplicationArn";
    private static final String PRINCIPAL = "principal";
    private static final Long CREATED_TIME = 1697824935000L;
    private static final Long UPDATED_TIME = 1697839335000L;
    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<QBusinessClient> proxyClient;

    @Mock
    QBusinessClient qBusinessClient;
    private CreateHandler underTest;
    private ResourceHandlerRequest<ResourceModel> request;
    private software.amazon.awssdk.services.qbusiness.model.ActionConfiguration actionConfiguration;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        qBusinessClient = mock(QBusinessClient.class);
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        underTest = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .applicationId(APPLICATION_ID)
            .dataAccessorId(DATAACCESSOR_ID)
            .displayName(DATAACCESSOR_NAME)
            .dataAccessorArn(DATAACCESSOR_ARN)
            .idcApplicationArn(IDC_APPLICATION_ARN)
            .principal(PRINCIPAL)
            .createdAt(Instant.ofEpochMilli(CREATED_TIME).toString())
            .updatedAt(Instant.ofEpochMilli(UPDATED_TIME).toString())
            .actionConfigurations(Collections.singletonList(ActionConfiguration.builder().action("test-action").build()))
            .build();

        actionConfiguration = software.amazon.awssdk.services.qbusiness.model.ActionConfiguration.builder()
            .action("test-action")
            .build();

        request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
    }

    @AfterEach
    public void tear_down() {
        verify(qBusinessClient, atLeastOnce()).serviceName();
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        when(proxyClient.client().createDataAccessor(any(CreateDataAccessorRequest.class)))
            .thenReturn(CreateDataAccessorResponse.builder()
                .dataAccessorId(DATAACCESSOR_ID)
                .build());
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

        final ProgressEvent<ResourceModel, CallbackContext> response = underTest.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }
}
