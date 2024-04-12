package software.amazon.qbusiness.plugin;

import org.apache.commons.lang3.StringUtils;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;

public class CustomPluginConfigHelper {

    public static CustomPluginConfiguration convertFromServiceCustomPluginConfig(
            software.amazon.awssdk.services.qbusiness.model.CustomPluginConfiguration customPluginConfig
    ) {
        if (customPluginConfig == null) {
            return null;
        }

        return CustomPluginConfiguration.builder()
                .apiSchemaType(customPluginConfig.apiSchemaTypeAsString())
                .apiSchema(convertFromServiceApiSchema(customPluginConfig.apiSchema()))
                .description(customPluginConfig.description())
                .build();
    }

    public static software.amazon.awssdk.services.qbusiness.model.CustomPluginConfiguration convertToServiceCustomPluginConfig(
            CustomPluginConfiguration customPluginConfig
    ) {
        if (customPluginConfig == null) {
            return null;
        }

        return software.amazon.awssdk.services.qbusiness.model.CustomPluginConfiguration.builder()
                .apiSchemaType(customPluginConfig.getApiSchemaType())
                .apiSchema(convertToServiceApiSchema(customPluginConfig.getApiSchema()))
                .description(customPluginConfig.getDescription())
                .build();
    }

    public static APISchema convertFromServiceApiSchema(
            software.amazon.awssdk.services.qbusiness.model.APISchema apiSchema
    ) {
        if (apiSchema == null) {
            return null;
        }

        final APISchema.APISchemaBuilder builder = APISchema.builder();
        if (StringUtils.isNotBlank(apiSchema.payload())) {
            builder.payload(apiSchema.payload());
        }

        if (apiSchema.s3() != null) {
            builder.s3(S3.builder()
                .bucket(apiSchema.s3().bucket())
                .key(apiSchema.s3().key())
                .build());
        }

        return builder.build();
    }

    public static software.amazon.awssdk.services.qbusiness.model.APISchema convertToServiceApiSchema(
            APISchema apiSchema
    ) {
        if (apiSchema == null) {
            return null;
        }

        if (apiSchema.getPayload() != null) {
            return software.amazon.awssdk.services.qbusiness.model.APISchema.builder()
                    .payload(apiSchema.getPayload())
                    .build();
        }

        if (apiSchema.getS3() != null) {
            return software.amazon.awssdk.services.qbusiness.model.APISchema.builder()
                    .s3(software.amazon.awssdk.services.qbusiness.model.S3.builder()
                            .bucket(apiSchema.getS3().getBucket())
                            .key(apiSchema.getS3().getKey())
                            .build())
                    .build();
        }

        throw new CfnGeneralServiceException("Unknown Api Schema");
    }

}
