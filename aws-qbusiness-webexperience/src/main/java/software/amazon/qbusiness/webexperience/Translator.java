package software.amazon.qbusiness.webexperience;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import software.amazon.awssdk.services.qbusiness.model.CreateWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListWebExperiencesRequest;
import software.amazon.awssdk.services.qbusiness.model.ListWebExperiencesResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateWebExperienceRequest;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.qbusiness.common.TagUtils;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */

public class Translator {

  /**
   * Request to create a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateWebExperienceRequest translateToCreateRequest(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    return CreateWebExperienceRequest.builder()
        .clientToken(request.getClientRequestToken())
        .applicationId(model.getApplicationId())
        .roleArn(model.getRoleArn())
        .identityProviderConfiguration(toIdentityProviderConfiguration(model.getIdentityProviderConfiguration()))
        .title(model.getTitle())
        .subtitle(model.getSubtitle())
        .welcomeMessage(model.getWelcomeMessage())
        .origins(model.getOrigins())
        .tags(TagUtils.mergeCreateHandlerTagsToSdkTags(request, model))
        .customizationConfiguration(toCustomizationConfiguration(model.getCustomizationConfiguration()))
        .browserExtensionConfiguration(toBrowserExtensionConfiguration(model.getBrowserExtensionConfiguration()))
        .build();
  }

  /**
   * Request to read a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static GetWebExperienceRequest translateToReadRequest(final ResourceModel model) {
    return GetWebExperienceRequest.builder()
        .applicationId(model.getApplicationId())
        .webExperienceId(model.getWebExperienceId())
        .build();
  }

  /**
   * Request to list tags for a resource
   *
   * @param request resource handler request for the resource model
   * @param model   resource model
   * @return awsRequest the aws service request to list tags for a resource
   */
  static ListTagsForResourceRequest translateToListTagsRequest(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var webExperienceArn = Utils.buildWebExperienceArn(request, model);

    return ListTagsForResourceRequest.builder()
        .resourceARN(webExperienceArn)
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   *
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetWebExperienceResponse awsResponse) {
    return ResourceModel.builder()
        .applicationId(awsResponse.applicationId())
        .webExperienceId(awsResponse.webExperienceId())
        .webExperienceArn(awsResponse.webExperienceArn())
        .status(awsResponse.statusAsString())
        .title(awsResponse.title())
        .subtitle(awsResponse.subtitle())
        .welcomeMessage(awsResponse.welcomeMessage())
        .samplePromptsControlMode(awsResponse.samplePromptsControlModeAsString())
        .roleArn(awsResponse.roleArn())
        .identityProviderConfiguration(fromIdentityProviderConfiguration(awsResponse.identityProviderConfiguration()))
        .defaultEndpoint(awsResponse.defaultEndpoint())
        .createdAt(instantToString(awsResponse.createdAt()))
        .updatedAt(instantToString(awsResponse.updatedAt()))
        .origins(awsResponse.origins())
        .customizationConfiguration(fromCustomizationConfiguration(awsResponse.customizationConfiguration()))
        .browserExtensionConfiguration(fromBrowserExtensionConfiguration(awsResponse.browserExtensionConfiguration()))
        .build();
  }

  /**
   * Request to add tags to resource model
   *
   * @param listTagsResponse response from list tags
   * @param model            resource model
   * @return model with tags added if they exist
   */
  static ResourceModel translateFromReadResponseWithTags(final ListTagsForResourceResponse listTagsResponse, final ResourceModel model) {
    if (listTagsResponse == null || !listTagsResponse.hasTags()) {
      return model;
    }

    return model.toBuilder()
        .tags(TagHelper.modelTagsFromServiceTags(listTagsResponse.tags()))
        .build();
  }

  /**
   * Request to delete a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteWebExperienceRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteWebExperienceRequest.builder()
        .applicationId(model.getApplicationId())
        .webExperienceId(model.getWebExperienceId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateWebExperienceRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateWebExperienceRequest.builder()
        .applicationId(model.getApplicationId())
        .webExperienceId(model.getWebExperienceId())
        .title(model.getTitle())
        .subtitle(model.getSubtitle())
        .roleArn(model.getRoleArn())
        .identityProviderConfiguration(toIdentityProviderConfiguration(model.getIdentityProviderConfiguration()))
        .origins(model.getOrigins())
        .customizationConfiguration(toCustomizationConfiguration(model.getCustomizationConfiguration()))
        .browserExtensionConfiguration(toBrowserExtensionConfiguration(model.getBrowserExtensionConfiguration()))
        .build();
  }

  static IdentityProviderConfiguration fromIdentityProviderConfiguration(
          software.amazon.awssdk.services.qbusiness.model.IdentityProviderConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return IdentityProviderConfiguration.builder()
            .openIDConnectConfiguration(fromOpenIDConnectProviderConfiguration(serviceConfig.openIDConnectConfiguration()))
            .samlConfiguration(fromSamlProviderConfiguration(serviceConfig.samlConfiguration()))
            .build();
  }

  static OpenIDConnectProviderConfiguration fromOpenIDConnectProviderConfiguration(
          software.amazon.awssdk.services.qbusiness.model.OpenIDConnectProviderConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return OpenIDConnectProviderConfiguration.builder()
            .secretsArn(serviceConfig.secretsArn())
            .secretsRole(serviceConfig.secretsRole())
            .build();
  }

  static SamlProviderConfiguration fromSamlProviderConfiguration(
          software.amazon.awssdk.services.qbusiness.model.SamlProviderConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return SamlProviderConfiguration.builder()
            .authenticationUrl(serviceConfig.authenticationUrl())
            .build();
  }

  static CustomizationConfiguration fromCustomizationConfiguration(
      software.amazon.awssdk.services.qbusiness.model.CustomizationConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return CustomizationConfiguration.builder()
            .customCSSUrl(serviceConfig.customCSSUrl())
            .logoUrl(serviceConfig.logoUrl())
            .fontUrl(serviceConfig.fontUrl())
            .faviconUrl(serviceConfig.faviconUrl())
            .build();
  }

  static software.amazon.qbusiness.webexperience.BrowserExtensionConfiguration fromBrowserExtensionConfiguration(
          software.amazon.awssdk.services.qbusiness.model.BrowserExtensionConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return software.amazon.qbusiness.webexperience.BrowserExtensionConfiguration.builder()
            .enabledBrowserExtensions(Set.copyOf(serviceConfig.enabledBrowserExtensionsAsStrings()))
            .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.BrowserExtensionConfiguration toBrowserExtensionConfiguration(
          BrowserExtensionConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.BrowserExtensionConfiguration.builder()
            .enabledBrowserExtensionsWithStrings(modelConfig.getEnabledBrowserExtensions())
            .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.OpenIDConnectProviderConfiguration toOpenIDConnectProviderConfiguration(
          OpenIDConnectProviderConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.OpenIDConnectProviderConfiguration.builder()
            .secretsArn(modelConfig.getSecretsArn())
            .secretsRole(modelConfig.getSecretsRole())
            .build();
  }


  static software.amazon.awssdk.services.qbusiness.model.SamlProviderConfiguration toSamlProviderConfiguration(
          SamlProviderConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.SamlProviderConfiguration.builder()
            .authenticationUrl(modelConfig.getAuthenticationUrl())
            .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.IdentityProviderConfiguration toIdentityProviderConfiguration(
          IdentityProviderConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.IdentityProviderConfiguration.builder()
            .samlConfiguration(toSamlProviderConfiguration(modelConfig.getSamlConfiguration()))
            .openIDConnectConfiguration(toOpenIDConnectProviderConfiguration(modelConfig.getOpenIDConnectConfiguration()))
            .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.CustomizationConfiguration toCustomizationConfiguration(
      CustomizationConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.CustomizationConfiguration.builder()
            .customCSSUrl(modelConfig.getCustomCSSUrl())
            .logoUrl(modelConfig.getLogoUrl())
            .fontUrl(modelConfig.getFontUrl())
            .faviconUrl(modelConfig.getFaviconUrl())
            .build();
  }

  /**
   * Request to list resources
   *
   * @param nextToken token passed to the aws service list resources request
   * @param model     resource model
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListWebExperiencesRequest translateToListRequest(final String nextToken, final ResourceModel model) {
    return ListWebExperiencesRequest.builder()
        .nextToken(nextToken)
        .applicationId(model.getApplicationId())
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   *
   * @param serviceResponse the aws service get resource response
   * @param applicationId   of the list of indices
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(final ListWebExperiencesResponse serviceResponse, final String applicationId) {
    return serviceResponse.webExperiences()
        .stream()
        .map(summary -> ResourceModel.builder()
            .applicationId(applicationId)
            .webExperienceId(summary.webExperienceId())
            .status(summary.statusAsString())
            .createdAt(instantToString(summary.createdAt()))
            .updatedAt(instantToString(summary.updatedAt()))
            .defaultEndpoint(summary.defaultEndpoint())
            .build())
        .toList();
  }

  private static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }
}
