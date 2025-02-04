package software.amazon.qbusiness.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.AssociatePermissionRequest;
import software.amazon.awssdk.services.qbusiness.model.AssociatePermissionResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {
    private static final String APPLICATION_ID = "ApplicationId";
    private static final String STATEMENT_ID = "StatementId";
    private static final String PRINCIPAL = "principal";
    private static final List<String> ACTIONS =List.of("actions");

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<QBusinessClient> proxyClient;

    @Mock
    QBusinessClient qBusinessClient;
    private CreateHandler underTest;
    private ResourceHandlerRequest<ResourceModel> request;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        qBusinessClient = mock(QBusinessClient.class);
        proxyClient = MOCK_PROXY(proxy, qBusinessClient);
        underTest = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
            .applicationId(APPLICATION_ID)
            .statementId(STATEMENT_ID)
            .principal(PRINCIPAL)
            .actions(ACTIONS)
            .build();

        request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();
    }

    @AfterEach
    public void tear_down() {
        verify(qBusinessClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(qBusinessClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        when(proxyClient.client().associatePermission(any(AssociatePermissionRequest.class)))
            .thenReturn(AssociatePermissionResponse.builder()
                .statement("policystatement")
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
