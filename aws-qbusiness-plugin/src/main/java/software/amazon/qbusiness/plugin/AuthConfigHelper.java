package software.amazon.qbusiness.plugin;

import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;

import java.util.Collections;
import java.util.Map;

public class AuthConfigHelper {

  public static PluginAuthConfiguration convertFromServiceAuthConfig(
      software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration serviceAuthConfig
  ) {

    final PluginAuthConfiguration.PluginAuthConfigurationBuilder builder = PluginAuthConfiguration.builder();

    if (serviceAuthConfig.oAuth2ClientCredentialConfiguration() != null) {
      builder.oAuth2ClientCredentialConfiguration(
              convertFromServiceOAuth(serviceAuthConfig.oAuth2ClientCredentialConfiguration()));
    }

    if (serviceAuthConfig.basicAuthConfiguration() != null) {
      builder.basicAuthConfiguration(
              convertFromServiceBasicAuth(serviceAuthConfig.basicAuthConfiguration()));
    }

    if (serviceAuthConfig.noAuthConfiguration() != null) {
      builder.noAuthConfiguration(convertFromServiceNoAuth(serviceAuthConfig.noAuthConfiguration()));
    }

    return builder.build();
  }

  public static software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration convertToServiceAuthConfig(
      PluginAuthConfiguration cfnAuthConfig
  ) {
    if (cfnAuthConfig == null) {
      return null;
    }

    if (cfnAuthConfig.getOAuth2ClientCredentialConfiguration() != null) {
      return software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration.builder()
          .oAuth2ClientCredentialConfiguration(convertToServiceOath(cfnAuthConfig.getOAuth2ClientCredentialConfiguration()))
          .build();
    }

    if (cfnAuthConfig.getBasicAuthConfiguration() != null) {
      return software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration.builder()
          .basicAuthConfiguration(convertToServiceBasicAuth(cfnAuthConfig.getBasicAuthConfiguration()))
          .build();
    }

    if (cfnAuthConfig.getNoAuthConfiguration() != null) {
      return software.amazon.awssdk.services.qbusiness.model.PluginAuthConfiguration.builder()
              .noAuthConfiguration(convertToServiceNoAuth(cfnAuthConfig.getNoAuthConfiguration()))
              .build();
    }
    throw new CfnGeneralServiceException("Unknown auth configuration");
  }

  private static BasicAuthConfiguration convertFromServiceBasicAuth(
      software.amazon.awssdk.services.qbusiness.model.BasicAuthConfiguration serviceBasicAuth
  ) {
    return BasicAuthConfiguration.builder()
        .roleArn(serviceBasicAuth.roleArn())
        .secretArn(serviceBasicAuth.secretArn())
        .build();
  }

  private static OAuth2ClientCredentialConfiguration convertFromServiceOAuth(
      software.amazon.awssdk.services.qbusiness.model.OAuth2ClientCredentialConfiguration serviceOath
  ) {
    return OAuth2ClientCredentialConfiguration.builder()
        .roleArn(serviceOath.roleArn())
        .secretArn(serviceOath.secretArn())
        .tokenUrl(serviceOath.tokenUrl())
        .authorizationUrl(serviceOath.authorizationUrl())
        .build();
  }

  private static Map<String, Object> convertFromServiceNoAuth(
          software.amazon.awssdk.services.qbusiness.model.NoAuthConfiguration serviceNoAuth
  ) {
    return Collections.emptyMap();
  }

  private static software.amazon.awssdk.services.qbusiness.model.BasicAuthConfiguration convertToServiceBasicAuth(
      BasicAuthConfiguration cfnBasicAuth
  ) {
    return software.amazon.awssdk.services.qbusiness.model.BasicAuthConfiguration.builder()
        .roleArn(cfnBasicAuth.getRoleArn())
        .secretArn(cfnBasicAuth.getSecretArn())
        .build();
  }

  private static software.amazon.awssdk.services.qbusiness.model.OAuth2ClientCredentialConfiguration convertToServiceOath(
      OAuth2ClientCredentialConfiguration cfnOath
  ) {
    return software.amazon.awssdk.services.qbusiness.model.OAuth2ClientCredentialConfiguration.builder()
        .roleArn(cfnOath.getRoleArn())
        .secretArn(cfnOath.getSecretArn())
        .tokenUrl(cfnOath.getTokenUrl())
        .authorizationUrl(cfnOath.getAuthorizationUrl())
        .build();
  }

  private static software.amazon.awssdk.services.qbusiness.model.NoAuthConfiguration convertToServiceNoAuth(
      Map<String, Object> cfnNoAuth
  ) {
    return software.amazon.awssdk.services.qbusiness.model.NoAuthConfiguration.builder()
            .build();
  }

}
