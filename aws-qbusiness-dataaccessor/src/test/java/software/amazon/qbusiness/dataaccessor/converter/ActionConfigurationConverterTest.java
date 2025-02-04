package software.amazon.qbusiness.dataaccessor.converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.qbusiness.dataaccessor.ActionConfiguration;
import software.amazon.qbusiness.dataaccessor.ActionFilterConfiguration;
import software.amazon.qbusiness.dataaccessor.AttributeFilter;
import software.amazon.qbusiness.dataaccessor.DocumentAttribute;
import software.amazon.qbusiness.dataaccessor.DocumentAttributeValue;

@ExtendWith(MockitoExtension.class)
class ActionConfigurationConverterTest {

  private static final String TEST_ACTION = "testAction";
  private static final String TEST_ATTRIBUTE = "testAttribute";
  private static final String TEST_VALUE = "testValue";
  private static final String TEST_ACTION_2 = "testAction2";

  @Test
  void testToServiceActionConfigurations() {
    List<ActionConfiguration> modelActionConfigurations = Collections.singletonList(
        ActionConfiguration.builder()
            .action(TEST_ACTION)
            .filterConfiguration(ActionFilterConfiguration.builder()
                .documentAttributeFilter(AttributeFilter.builder()
                    .equalsTo(DocumentAttribute.builder()
                        .name(TEST_ATTRIBUTE)
                        .value(DocumentAttributeValue.builder()
                            .stringValue(TEST_VALUE)
                            .build())
                        .build())
                    .build())
                .build())
            .build());

    List<software.amazon.awssdk.services.qbusiness.model.ActionConfiguration> serviceActionConfigurations =
        ActionConfigurationConverter.toServiceActionConfigurations(modelActionConfigurations);

    Assertions.assertNotNull(serviceActionConfigurations);
    Assertions.assertEquals(1, serviceActionConfigurations.size());
    Assertions.assertEquals(TEST_ACTION, serviceActionConfigurations.get(0).action());
    Assertions.assertEquals(TEST_ATTRIBUTE, serviceActionConfigurations.get(0).filterConfiguration().documentAttributeFilter().equalsTo().name());
    Assertions.assertEquals(TEST_VALUE, serviceActionConfigurations.get(0).filterConfiguration().documentAttributeFilter().equalsTo().value().stringValue());
  }

  @Test
  void testFromServiceActionConfigurations() {
    List<software.amazon.awssdk.services.qbusiness.model.ActionConfiguration> serviceActionConfigurations = Arrays.asList(
        software.amazon.awssdk.services.qbusiness.model.ActionConfiguration.builder()
            .action(TEST_ACTION)
            .filterConfiguration(software.amazon.awssdk.services.qbusiness.model.ActionFilterConfiguration.builder()
                .documentAttributeFilter(software.amazon.awssdk.services.qbusiness.model.AttributeFilter.builder()
                    .equalsTo(software.amazon.awssdk.services.qbusiness.model.DocumentAttribute.builder()
                        .name(TEST_ATTRIBUTE)
                        .value(software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue.builder()
                            .stringValue(TEST_VALUE)
                            .build())
                        .build())
                    .build())
                .build())
            .build(),
        software.amazon.awssdk.services.qbusiness.model.ActionConfiguration.builder()
            .action(TEST_ACTION_2)
            .build());

    List<ActionConfiguration> modelActionConfigurations = ActionConfigurationConverter.fromServiceActionConfigurations(serviceActionConfigurations);

    Assertions.assertNotNull(modelActionConfigurations);
    Assertions.assertEquals(2, modelActionConfigurations.size());

    ActionConfiguration firstModelActionConfiguration = modelActionConfigurations.get(0);
    Assertions.assertEquals(TEST_ACTION, firstModelActionConfiguration.getAction());
    Assertions.assertNotNull(firstModelActionConfiguration.getFilterConfiguration());
    Assertions.assertNotNull(firstModelActionConfiguration.getFilterConfiguration().getDocumentAttributeFilter());
    Assertions.assertEquals(TEST_ATTRIBUTE, firstModelActionConfiguration.getFilterConfiguration().getDocumentAttributeFilter().getEqualsTo().getName());
    Assertions.assertEquals(TEST_VALUE, firstModelActionConfiguration.getFilterConfiguration().getDocumentAttributeFilter().getEqualsTo().getValue().getStringValue());

    ActionConfiguration secondModelActionConfiguration = modelActionConfigurations.get(1);
    Assertions.assertEquals(TEST_ACTION_2, secondModelActionConfiguration.getAction());
    Assertions.assertNull(secondModelActionConfiguration.getFilterConfiguration());
  }
}
