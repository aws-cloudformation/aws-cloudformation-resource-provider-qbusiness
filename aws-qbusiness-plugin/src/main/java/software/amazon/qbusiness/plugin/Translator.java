package software.amazon.qbusiness.plugin;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.services.qbusiness.model.CreatePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.DeletePluginRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPluginResponse;
import software.amazon.awssdk.services.qbusiness.model.ListPluginsRequest;
import software.amazon.awssdk.services.qbusiness.model.ListPluginsResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdatePluginRequest;
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
  static CreatePluginRequest translateToCreateRequest(final ResourceModel model, ResourceHandlerRequest<ResourceModel> request) {
    return CreatePluginRequest.builder()
        .applicationId(model.getApplicationId())
        .displayName(model.getDisplayName())
        .type(model.getType())
        .serverUrl(model.getServerUrl())
        .authConfiguration(AuthConfigHelper.convertToServiceAuthConfig(model.getAuthConfiguration()))
        .customPluginConfiguration(CustomPluginConfigHelper.convertToServiceCustomPluginConfig(model.getCustomPluginConfiguration()))
        .clientToken(request.getClientRequestToken())
        .tags(TagUtils.mergeCreateHandlerTagsToSdkTags(request, model))
        .build();
  }

  static UpdatePluginRequest translateToPostCreateUpdateRequest(final ResourceModel model) {
    return UpdatePluginRequest.builder()
        .applicationId(model.getApplicationId())
        .pluginId(model.getPluginId())
        .state(model.getState())
        .build();
  }

  /**
   * Request to read a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static GetPluginRequest translateToReadRequest(final ResourceModel model) {
    return GetPluginRequest.builder()
        .applicationId(model.getApplicationId())
        .pluginId(model.getPluginId())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   *
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetPluginResponse awsResponse) {
    return ResourceModel.builder()
        .applicationId(awsResponse.applicationId())
        .pluginId(awsResponse.pluginId())
        .pluginArn(awsResponse.pluginArn())
        .type(awsResponse.typeAsString())
        .displayName(awsResponse.displayName())
        .serverUrl(awsResponse.serverUrl())
        .authConfiguration(AuthConfigHelper.convertFromServiceAuthConfig(awsResponse.authConfiguration()))
        .state(awsResponse.stateAsString())
        .buildStatus(awsResponse.buildStatusAsString())
        .customPluginConfiguration(CustomPluginConfigHelper.convertFromServiceCustomPluginConfig(awsResponse.customPluginConfiguration()))
        .createdAt(instantToString(awsResponse.createdAt()))
        .updatedAt(instantToString(awsResponse.updatedAt()))
        .build();
  }

  /**
   * Request to delete a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeletePluginRequest translateToDeleteRequest(final ResourceModel model) {
    return DeletePluginRequest.builder()
        .pluginId(model.getPluginId())
        .applicationId(model.getApplicationId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdatePluginRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdatePluginRequest.builder()
        .applicationId(model.getApplicationId())
        .pluginId(model.getPluginId())
        .displayName(model.getDisplayName())
        .serverUrl(model.getServerUrl())
        .authConfiguration(AuthConfigHelper.convertToServiceAuthConfig(model.getAuthConfiguration()))
        .customPluginConfiguration(CustomPluginConfigHelper.convertToServiceCustomPluginConfig(model.getCustomPluginConfiguration()))
        .state(model.getState())
        .build();
  }

  /**
   * Request to list resources
   *
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListPluginsRequest translateToListRequest(final String applicationId, final String nextToken) {
    return ListPluginsRequest.builder()
        .applicationId(applicationId)
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   *
   * @param listPluginsResponse the aws service list resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(final String applicationId, final ListPluginsResponse listPluginsResponse) {
    return listPluginsResponse.plugins()
        .stream()
        .map(plugin -> ResourceModel.builder()
            .applicationId(applicationId)
            .pluginId(plugin.pluginId())
            .displayName(plugin.displayName())
            .type(plugin.type().toString())
            .serverUrl(plugin.serverUrl())
            .state(plugin.stateAsString())
            .buildStatus(plugin.buildStatusAsString())
            .createdAt(instantToString(plugin.createdAt()))
            .updatedAt(instantToString(plugin.updatedAt()))
            .build())
        .toList();

  }

  static ResourceModel translateFromReadResponseWithTags(final ListTagsForResourceResponse listTagsResponse, final ResourceModel model) {
    if (listTagsResponse == null || !listTagsResponse.hasTags()) {
      return model;
    }

    return model.toBuilder()
        .tags(TagHelper.cfnTagsFromServiceTags(listTagsResponse.tags()))
        .build();
  }

  static ListTagsForResourceRequest translateToListTagsRequest(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var pluginArn = model.getPluginArn();
    return ListTagsForResourceRequest.builder()
        .resourceARN(pluginArn)
        .build();
  }

  static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }

}
