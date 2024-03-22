package software.amazon.qbusiness.datasource.translators;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import software.amazon.qbusiness.datasource.DocumentAttributeCondition;
import software.amazon.qbusiness.datasource.DocumentAttributeTarget;
import software.amazon.qbusiness.datasource.DocumentAttributeValue;
import software.amazon.qbusiness.datasource.DocumentEnrichmentConfiguration;
import software.amazon.qbusiness.datasource.HookConfiguration;
import software.amazon.qbusiness.datasource.InlineDocumentEnrichmentConfiguration;
import software.amazon.qbusiness.datasource.Translator;

public final class DocumentEnrichmentTranslator {

  private DocumentEnrichmentTranslator() {
  }

  public static DocumentEnrichmentConfiguration fromServiceDocEnrichmentConf(
      software.amazon.awssdk.services.qbusiness.model.DocumentEnrichmentConfiguration maybeServiceConf
  ) {
    return Optional.ofNullable(maybeServiceConf)
        .map(serviceConf -> DocumentEnrichmentConfiguration.builder()
            .inlineConfigurations(fromServiceInlineEnrichmentConfs(serviceConf.inlineConfigurations()))
            .preExtractionHookConfiguration(fromServiceHookConfiguration(serviceConf.preExtractionHookConfiguration()))
            .postExtractionHookConfiguration(fromServiceHookConfiguration(serviceConf.postExtractionHookConfiguration()))
            .build())
        .orElse(null);
  }

  public static software.amazon.awssdk.services.qbusiness.model.DocumentEnrichmentConfiguration toServiceDocEnrichmentConf(
      DocumentEnrichmentConfiguration modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.DocumentEnrichmentConfiguration.builder()
        .inlineConfigurations(toServiceInlineEnrichmentConfs(modelData.getInlineConfigurations()))
        .preExtractionHookConfiguration(toServiceHookConfiguration(modelData.getPreExtractionHookConfiguration()))
        .postExtractionHookConfiguration(toServiceHookConfiguration(modelData.getPostExtractionHookConfiguration()))
        .build();
  }

  static HookConfiguration fromServiceHookConfiguration(
      software.amazon.awssdk.services.qbusiness.model.HookConfiguration serviceHookConfiguration
  ) {
    if (serviceHookConfiguration == null) {
      return null;
    }

    return HookConfiguration.builder()
        .lambdaArn(serviceHookConfiguration.lambdaArn())
        .roleArn(serviceHookConfiguration.roleArn())
        .s3BucketName(serviceHookConfiguration.s3BucketName())
        .invocationCondition(fromServiceDocumentAttributeCondition(serviceHookConfiguration.invocationCondition()))
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.HookConfiguration toServiceHookConfiguration(
      HookConfiguration modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.HookConfiguration.builder()
        .lambdaArn(modelData.getLambdaArn())
        .roleArn(modelData.getRoleArn())
        .s3BucketName(modelData.getS3BucketName())
        .invocationCondition(toServiceDocumentAttributeCondition(modelData.getInvocationCondition()))
        .build();
  }

  static List<InlineDocumentEnrichmentConfiguration> fromServiceInlineEnrichmentConfs(
      List<software.amazon.awssdk.services.qbusiness.model.InlineDocumentEnrichmentConfiguration> serviceInlineConfs
  ) {
    if (serviceInlineConfs == null) {
      return null;
    }

    return serviceInlineConfs.stream()
        .map(inlineConf -> InlineDocumentEnrichmentConfiguration.builder()
            .documentContentOperator(inlineConf.documentContentOperatorAsString())
            .target(fromServiceDocAttributeTarget(inlineConf.target()))
            .condition(fromServiceDocumentAttributeCondition(inlineConf.condition()))
            .build())
        .toList();
  }

  static List<software.amazon.awssdk.services.qbusiness.model.InlineDocumentEnrichmentConfiguration> toServiceInlineEnrichmentConfs(
      List<InlineDocumentEnrichmentConfiguration> modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return modelData.stream()
        .map(modelInlineConf -> software.amazon.awssdk.services.qbusiness.model.InlineDocumentEnrichmentConfiguration.builder()
            .documentContentOperator(modelInlineConf.getDocumentContentOperator())
            .target(toServiceDocAttributeTarget(modelInlineConf.getTarget()))
            .condition(toServiceDocumentAttributeCondition(modelInlineConf.getCondition()))
            .build()
        )
        .toList();
  }

  static DocumentAttributeCondition fromServiceDocumentAttributeCondition(
      software.amazon.awssdk.services.qbusiness.model.DocumentAttributeCondition serviceDocumentAttributeCondition
  ) {
    if (serviceDocumentAttributeCondition == null) {
      return null;
    }

    return DocumentAttributeCondition.builder()
        .operator(serviceDocumentAttributeCondition.operatorAsString())
        .value(fromServiceTargetDocumentAttributeValue(serviceDocumentAttributeCondition.value()))
        .key(serviceDocumentAttributeCondition.key())
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.DocumentAttributeCondition toServiceDocumentAttributeCondition(
      DocumentAttributeCondition modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.DocumentAttributeCondition.builder()
        .operator(modelData.getOperator())
        .value(toServiceTargetDocumentAttributeValue(modelData.getValue()))
        .key(modelData.getKey())
        .build();
  }

  static DocumentAttributeTarget fromServiceDocAttributeTarget(
      software.amazon.awssdk.services.qbusiness.model.DocumentAttributeTarget serviceData
  ) {
    if (serviceData == null) {
      return null;
    }

    return DocumentAttributeTarget.builder()
        .key(serviceData.key())
        .attributeValueOperator(serviceData.attributeValueOperatorAsString())
        .value(fromServiceTargetDocumentAttributeValue(serviceData.value()))
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.DocumentAttributeTarget toServiceDocAttributeTarget(
      DocumentAttributeTarget modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.DocumentAttributeTarget.builder()
        .key(modelData.getKey())
        .attributeValueOperator(modelData.getAttributeValueOperator())
        .value(toServiceTargetDocumentAttributeValue(modelData.getValue()))
        .build();
  }

  static DocumentAttributeValue fromServiceTargetDocumentAttributeValue(
      software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue serviceData
  ) {
    if (serviceData == null) {
      return null;
    }

    DocumentAttributeValue.DocumentAttributeValueBuilder builder = DocumentAttributeValue.builder();

    Double longAsDouble = Optional.ofNullable(serviceData.longValue()).map(Long::doubleValue).orElse(null);

    if (longAsDouble != null) {
      builder.longValue(longAsDouble);
    }

    if (serviceData.dateValue() != null) {
      builder.dateValue(Translator.instantToString(serviceData.dateValue()));
    }

    if (StringUtils.isNotBlank(serviceData.stringValue())) {
      builder.stringValue(serviceData.stringValue());
    }

    if (serviceData.stringListValue() != null && !serviceData.stringListValue().isEmpty()) {
      builder.stringListValue(serviceData.stringListValue());
    }


    return builder.build();
  }

  static software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue toServiceTargetDocumentAttributeValue(
      DocumentAttributeValue modelData
  ) {
    if (modelData == null) {
      return null;
    }

    final Long longValue = Optional.ofNullable(modelData.getLongValue()).map(Double::longValue).orElse(null);
    final Instant dateValue = Optional.ofNullable(modelData.getDateValue()).map(Instant::parse).orElse(null);
    final String stringValue = modelData.getStringValue();
    final List<String> stringListValue = modelData.getStringListValue() != null && !modelData.getStringListValue().isEmpty()
            ? modelData.getStringListValue()
            : null;

    return software.amazon.awssdk.services.qbusiness.model.DocumentAttributeValue.builder()
        .dateValue(dateValue)
        .longValue(longValue)
        .stringValue(stringValue)
        .stringListValue(stringListValue)
        .build();
  }
}
