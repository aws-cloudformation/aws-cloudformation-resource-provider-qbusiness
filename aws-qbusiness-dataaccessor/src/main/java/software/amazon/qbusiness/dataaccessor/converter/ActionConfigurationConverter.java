package software.amazon.qbusiness.dataaccessor.converter;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.qbusiness.dataaccessor.ActionConfiguration;
import software.amazon.qbusiness.dataaccessor.ActionFilterConfiguration;
import software.amazon.qbusiness.dataaccessor.AttributeFilter;
import software.amazon.qbusiness.dataaccessor.DocumentAttribute;
import software.amazon.qbusiness.dataaccessor.DocumentAttributeValue;

public class ActionConfigurationConverter {

  public static List<software.amazon.awssdk.services.qbusiness.model.ActionConfiguration> toServiceActionConfigurations(
      List<ActionConfiguration> modelActionConfigurations) {
    return modelActionConfigurations.stream()
        .map(ActionConfigurationConverter::toServiceActionConfiguration)
        .collect(Collectors.toList());
  }

  public static List<ActionConfiguration> fromServiceActionConfigurations(
      List<software.amazon.awssdk.services.qbusiness.model.ActionConfiguration> serviceActionConfigurations) {
    return serviceActionConfigurations.stream()
        .map(ActionConfigurationConverter::fromServiceActionConfiguration)
        .collect(Collectors.toList());
  }

  private static software.amazon.awssdk.services.qbusiness.model.ActionConfiguration toServiceActionConfiguration(
      ActionConfiguration modelActionConfiguration) {
    if (modelActionConfiguration == null) {
      throw new CfnInvalidRequestException("ActionConfiguration is not provided.");
    }

    if (modelActionConfiguration.getAction() == null) {
      throw new CfnInvalidRequestException("Action is not provided in ActionConfiguration");
    }

    software.amazon.awssdk.services.qbusiness.model.ActionConfiguration.Builder builder =
        software.amazon.awssdk.services.qbusiness.model.ActionConfiguration.builder()
            .action(modelActionConfiguration.getAction());

    if (modelActionConfiguration.getFilterConfiguration() != null) {
      if (modelActionConfiguration.getFilterConfiguration().getDocumentAttributeFilter() == null) {
        throw new CfnInvalidRequestException(
            "DocumentAttributeFilter is not provided in ActionFilterConfiguration");
      }
      builder.filterConfiguration(
          toServiceActionFilterConfiguration(modelActionConfiguration.getFilterConfiguration()));
    }

    return builder.build();
  }

  private static software.amazon.awssdk.services.qbusiness.model.ActionFilterConfiguration toServiceActionFilterConfiguration(
      ActionFilterConfiguration modelActionFilterConfiguration) {
    if (modelActionFilterConfiguration == null) {
      throw new CfnInvalidRequestException("ActionFilterConfiguration is not provided.");
    }

    if (modelActionFilterConfiguration.getDocumentAttributeFilter() == null) {
      throw new CfnInvalidRequestException(
          "DocumentAttributeFilter is not provided in ActionFilterConfiguration");
    }

    return software.amazon.awssdk.services.qbusiness.model.ActionFilterConfiguration.builder()
        .documentAttributeFilter(
            toServiceActionFilter(modelActionFilterConfiguration.getDocumentAttributeFilter()))
        .build();
  }

  private static software.amazon.awssdk.services.qbusiness.model.AttributeFilter toServiceActionFilter(
      final AttributeFilter filter) {
    if (filter == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.AttributeFilter.builder()
        .notFilter(toServiceActionFilter(filter.getNotFilter()))
        .andAllFilters(toServiceAttributeFilters(filter.getAndAllFilters()))
        .orAllFilters(toServiceAttributeFilters(filter.getOrAllFilters()))
        .containsAll(toServiceDocumentAttribute(filter.getContainsAll()))
        .containsAny(toServiceDocumentAttribute(filter.getContainsAny()))
        .equalsTo(toServiceDocumentAttribute(filter.getEqualsTo()))
        .greaterThan(toServiceDocumentAttribute(filter.getGreaterThan()))
        .greaterThanOrEquals(toServiceDocumentAttribute(filter.getGreaterThanOrEquals()))
        .lessThan(toServiceDocumentAttribute(filter.getLessThan()))
        .lessThanOrEquals(toServiceDocumentAttribute(filter.getLessThanOrEquals()))
        .build();
  }

  private static List<software.amazon.awssdk.services.qbusiness.model.AttributeFilter> toServiceAttributeFilters(
      final List<AttributeFilter> filters) {
    if (filters == null) {
      return null;
    }
    return filters.stream()
        .map(ActionConfigurationConverter::toServiceActionFilter)
        .collect(Collectors.toList());
  }

  private static software.amazon.awssdk.services.qbusiness.model.DocumentAttribute toServiceDocumentAttribute(
      final DocumentAttribute attribute) {
    if (attribute == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.DocumentAttribute.builder()
        .name(attribute.getName())
        .value(toServiceDocumentAttributeValue(attribute.getValue()))
        .build();
  }

  private static software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue toServiceDocumentAttributeValue(
      final DocumentAttributeValue attributeValue) {
    if (attributeValue == null) {
      return null;
    }

    software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue.Builder builder = software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue.builder()
        .stringValue(attributeValue.getStringValue())
        .stringListValue(attributeValue.getStringListValue());

    String dateValue = attributeValue.getDateValue();
    if (dateValue != null) {
      builder.dateValue(Instant.parse(dateValue));
    }

    Double longValue = attributeValue.getLongValue();
    if (longValue != null) {
      builder.longValue(longValue.longValue());
    }
    return builder.build();
  }

  private static ActionConfiguration fromServiceActionConfiguration(
      software.amazon.awssdk.services.qbusiness.model.ActionConfiguration serviceActionConfiguration) {
    if (serviceActionConfiguration == null) {
      throw new CfnInvalidRequestException("ActionConfiguration is not provided.");
    }

    if (serviceActionConfiguration.action() == null) {
      throw new CfnInvalidRequestException("Action is not provided in ActionConfiguration");
    }

    ActionConfiguration.ActionConfigurationBuilder builder = ActionConfiguration.builder()
        .action(serviceActionConfiguration.action());

    if (serviceActionConfiguration.filterConfiguration() != null) {
      builder.filterConfiguration(
          fromServiceActionFilterConfiguration(serviceActionConfiguration.filterConfiguration()));
    }

    return builder.build();
  }

  private static ActionFilterConfiguration fromServiceActionFilterConfiguration(
      software.amazon.awssdk.services.qbusiness.model.ActionFilterConfiguration serviceActionFilterConfiguration) {
    if (serviceActionFilterConfiguration == null) {
      throw new CfnInvalidRequestException("ActionFilterConfiguration is not provided.");
    }

    if (serviceActionFilterConfiguration.documentAttributeFilter() == null) {
      throw new CfnInvalidRequestException(
          "DocumentAttributeFilter is not provided in ActionFilterConfiguration");
    }

    return ActionFilterConfiguration.builder()
        .documentAttributeFilter(
            fromServiceActionFilter(serviceActionFilterConfiguration.documentAttributeFilter()))
        .build();
  }

  private static AttributeFilter fromServiceActionFilter(
      final software.amazon.awssdk.services.qbusiness.model.AttributeFilter filter) {
    if (filter == null) {
      return null;
    }

    AttributeFilter.AttributeFilterBuilder builder = AttributeFilter.builder();

    if (filter.notFilter() != null) {
      builder.notFilter(fromServiceActionFilter(filter.notFilter()));
    }
    if (filter.andAllFilters() != null && !filter.andAllFilters().isEmpty()) {
      builder.andAllFilters(fromServiceAttributeFilters(filter.andAllFilters()));
    }
    if (filter.orAllFilters() != null && !filter.orAllFilters().isEmpty()) {
      builder.orAllFilters(fromServiceAttributeFilters(filter.orAllFilters()));
    }
    if (filter.containsAll() != null) {
      builder.containsAll(fromServiceDocumentAttribute(filter.containsAll()));
    }
    if (filter.containsAny() != null) {
      builder.containsAny(fromServiceDocumentAttribute(filter.containsAny()));
    }
    if (filter.equalsTo() != null) {
      builder.equalsTo(fromServiceDocumentAttribute(filter.equalsTo()));
    }
    if (filter.greaterThan() != null) {
      builder.greaterThan(fromServiceDocumentAttribute(filter.greaterThan()));
    }
    if (filter.greaterThanOrEquals() != null) {
      builder.greaterThanOrEquals(fromServiceDocumentAttribute(filter.greaterThanOrEquals()));
    }
    if (filter.lessThan() != null) {
      builder.lessThan(fromServiceDocumentAttribute(filter.lessThan()));
    }
    if (filter.lessThanOrEquals() != null) {
      builder.lessThanOrEquals(fromServiceDocumentAttribute(filter.lessThanOrEquals()));
    }

    return builder.build();
  }

  private static List<AttributeFilter> fromServiceAttributeFilters(
      final List<software.amazon.awssdk.services.qbusiness.model.AttributeFilter> filters) {
    if (filters == null) {
      return null;
    }
    return filters.stream()
        .map(ActionConfigurationConverter::fromServiceActionFilter)
        .collect(Collectors.toList());
  }

  private static DocumentAttribute fromServiceDocumentAttribute(
      final software.amazon.awssdk.services.qbusiness.model.DocumentAttribute attribute) {
    if (attribute == null) {
      return null;
    }

    return DocumentAttribute.builder()
        .name(attribute.name())
        .value(fromServiceDocumentAttributeValue(attribute.value()))
        .build();
  }

  private static DocumentAttributeValue fromServiceDocumentAttributeValue(
      final software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue attributeValue) {
    if (attributeValue == null) {
      return null;
    }

    DocumentAttributeValue.DocumentAttributeValueBuilder builder = DocumentAttributeValue.builder();

    if (attributeValue.stringValue() != null) {
      builder.stringValue(attributeValue.stringValue());
    }

    if (attributeValue.stringListValue() != null && !attributeValue.stringListValue().isEmpty()) {
      builder.stringListValue(attributeValue.stringListValue());
    }

    if (attributeValue.dateValue() != null) {
      builder.dateValue(attributeValue.dateValue().toString());
    }

    if (attributeValue.longValue() != null) {
      builder.longValue(Double.valueOf(attributeValue.longValue()));
    }

    return builder.build();
  }

}
