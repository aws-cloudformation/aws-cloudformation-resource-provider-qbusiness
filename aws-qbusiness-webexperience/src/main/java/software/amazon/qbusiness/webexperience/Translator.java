package software.amazon.qbusiness.webexperience;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetWebExperienceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListWebExperiencesRequest;
import software.amazon.awssdk.services.qbusiness.model.ListWebExperiencesResponse;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateWebExperienceRequest;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

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
  static CreateWebExperienceRequest translateToCreateRequest(final String idempotentToken, final ResourceModel model) {
    return CreateWebExperienceRequest.builder()
        .clientToken(idempotentToken)
        .applicationId(model.getApplicationId())
        .roleArn(model.getRoleArn())
        .identityProviderConfiguration(toIdentityProviderConfiguration(model.getIdentityProviderConfiguration()))
        .title(model.getTitle())
        .subtitle(model.getSubtitle())
        .welcomeMessage(model.getWelcomeMessage())
        .origins(model.getOrigins())
        .tags(TagHelper.serviceTagsFromCfnTags(model.getTags()))
        .customizationConfiguration(toCustomizationConfiguration(model.getCustomizationConfiguration()))
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
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static AwsRequest translateToFirstUpdateRequest(final ResourceModel model) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L45-L50
    return awsRequest;
  }

  /**
   * Request to update some other properties that could not be provisioned through first update request
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static AwsRequest translateToSecondUpdateRequest(final ResourceModel model) {
    final AwsRequest awsRequest = null;
    // TODO: construct a request
    return awsRequest;
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

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  /**
   * Request to add tags to a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static TagResourceRequest tagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Map<String, String> addedTags) {
    var webExperienceArn = Utils.buildWebExperienceArn(request, model);

    List<Tag> toTags = Optional.ofNullable(addedTags)
        .map(Map::entrySet)
        .map(pairs -> pairs.stream()
            .map(pair -> Tag.builder()
                .key(pair.getKey())
                .value(pair.getValue())
                .build()
            )
            .toList()
        )
        .filter(list -> !list.isEmpty())
        .orElse(null);

    return TagResourceRequest.builder()
        .resourceARN(webExperienceArn)
        .tags(toTags)
        .build();
  }

  /**
   * Request to remove tags from a resource
   *
   * @param request request details
   * @param model resource model
   * @return UntagResourceRequest the aws service request to create a resource
   */
  static UntagResourceRequest untagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Set<String> removedTags) {
    var webExperienceArn = Utils.buildWebExperienceArn(request, model);
    var tagsToRemove = Optional.ofNullable(removedTags)
        .filter(set -> !set.isEmpty())
        .orElse(null);

    return UntagResourceRequest.builder()
        .resourceARN(webExperienceArn)
        .tagKeys(tagsToRemove)
        .build();
  }

  private static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }
}
