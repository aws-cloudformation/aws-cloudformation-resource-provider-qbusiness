package software.amazon.qbusiness.index;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.qbusiness.model.CreateIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexRequest;
import software.amazon.awssdk.services.qbusiness.model.GetIndexResponse;
import software.amazon.awssdk.services.qbusiness.model.ListIndicesRequest;
import software.amazon.awssdk.services.qbusiness.model.ListIndicesResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateIndexRequest;
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
  static CreateIndexRequest translateToCreateRequest(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    return CreateIndexRequest.builder()
        .clientToken(request.getClientRequestToken())
        .displayName(model.getDisplayName())
        .applicationId(model.getApplicationId())
        .description(model.getDescription())
        .type(model.getType())
        .capacityConfiguration(toServiceCapacityConfiguration(model.getCapacityConfiguration()))
        .tags(TagUtils.mergeCreateHandlerTagsToSdkTags(request, model))
        .build();
  }

  /**
   * Request to read a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static GetIndexRequest translateToReadRequest(final ResourceModel model) {
    return GetIndexRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
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
    var indexArn = Utils.buildIndexArn(request, model);

    return ListTagsForResourceRequest.builder()
        .resourceARN(indexArn)
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   *
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetIndexResponse awsResponse) {
    return ResourceModel.builder()
        .displayName(awsResponse.displayName())
        .applicationId(awsResponse.applicationId())
        .indexId(awsResponse.indexId())
        .indexArn(awsResponse.indexArn())
        .indexStatistics(fromServiceIndexStatistics(awsResponse.indexStatistics()))
        .status(awsResponse.statusAsString())
        .description(awsResponse.description())
        .type(awsResponse.typeAsString())
        .documentAttributeConfigurations(fromServiceDocumentAttributeConfigurations(awsResponse.documentAttributeConfigurations()))
        .createdAt(instantToString(awsResponse.createdAt()))
        .updatedAt(instantToString(awsResponse.updatedAt()))
        .capacityConfiguration(fromServiceCapacityConfiguration(awsResponse.capacityConfiguration()))
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
  static DeleteIndexRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteIndexRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateIndexRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateIndexRequest.builder()
        .displayName(model.getDisplayName())
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .description(model.getDescription())
        .documentAttributeConfigurations(toServiceDocumentAttributeConfigurations(model.getDocumentAttributeConfigurations()))
        .capacityConfiguration(toServiceCapacityConfiguration(model.getCapacityConfiguration()))
        .build();
  }

  static UpdateIndexRequest translateToPostCreateUpdateRequest(final ResourceModel model) {
    return UpdateIndexRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .documentAttributeConfigurations(toServiceDocumentAttributeConfigurations(model.getDocumentAttributeConfigurations()))
        .build();
  }

  /**
   * Request to list resources
   *
   * @param nextToken token passed to the aws service list resources request
   * @param model     resource model
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListIndicesRequest translateToListRequest(final String nextToken, final ResourceModel model) {
    return ListIndicesRequest.builder()
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
  static List<ResourceModel> translateFromListResponse(final ListIndicesResponse serviceResponse, final String applicationId) {
    return serviceResponse.indices()
        .stream()
        .map(index -> ResourceModel.builder()
            .indexId(index.indexId())
            .applicationId(applicationId)
            .createdAt(instantToString(index.createdAt()))
            .updatedAt(instantToString(index.updatedAt()))
            .displayName(index.displayName())
            .status(index.statusAsString())
            .build())
        .toList();
  }

  private static IndexCapacityConfiguration fromServiceCapacityConfiguration(
      final software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration indexCapacityConfiguration) {

    if (indexCapacityConfiguration == null) {
      return null;
    }

    return IndexCapacityConfiguration.builder()
        .units(Double.valueOf(indexCapacityConfiguration.units()))
        .build();
  }

  private static List<DocumentAttributeConfiguration> fromServiceDocumentAttributeConfigurations(
      final List<software.amazon.awssdk.services.qbusiness.model.DocumentAttributeConfiguration> documentAttributeConfigurations) {

    if (documentAttributeConfigurations == null) {
      return null;
    }

    return documentAttributeConfigurations.stream()
        .map(documentAttributeConfiguration -> DocumentAttributeConfiguration.builder()
            .name(documentAttributeConfiguration.name())
            .search(documentAttributeConfiguration.searchAsString())
            .type(documentAttributeConfiguration.typeAsString())
            .build())
        .collect(Collectors.toList());
  }

  private static List<software.amazon.awssdk.services.qbusiness.model.DocumentAttributeConfiguration>
  toServiceDocumentAttributeConfigurations(final List<DocumentAttributeConfiguration> documentAttributeConfigurations) {
    if (documentAttributeConfigurations == null) {
      return null;
    }

    return documentAttributeConfigurations.stream()
        .map(documentAttributeConfiguration -> software.amazon.awssdk.services.qbusiness.model.DocumentAttributeConfiguration.builder()
            .name(documentAttributeConfiguration.getName())
            .search(documentAttributeConfiguration.getSearch())
            .type(documentAttributeConfiguration.getType())
            .build())
        .collect(Collectors.toList());
  }

  private static IndexStatistics fromServiceIndexStatistics(
      final software.amazon.awssdk.services.qbusiness.model.IndexStatistics sdkIndexStatistics) {

    if (sdkIndexStatistics == null) {
      return null;
    }

    return IndexStatistics.builder()
        .textDocumentStatistics(fromServiceTextDocumentStatistics(sdkIndexStatistics.textDocumentStatistics()))
        .build();
  }

  private static TextDocumentStatistics fromServiceTextDocumentStatistics(
      final software.amazon.awssdk.services.qbusiness.model.TextDocumentStatistics sdkTextDocumentStatistics) {

    if (sdkTextDocumentStatistics == null) {
      return null;
    }

    return TextDocumentStatistics.builder()
        .indexedTextBytes(Double.valueOf(sdkTextDocumentStatistics.indexedTextBytes()))
        .indexedTextDocumentCount(Double.valueOf(sdkTextDocumentStatistics.indexedTextDocumentCount()))
        .build();
  }

  private static software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration toServiceCapacityConfiguration(
      final IndexCapacityConfiguration capacityConfiguration) {
    if (capacityConfiguration == null || capacityConfiguration.getUnits() == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.IndexCapacityConfiguration.builder()
        .units(capacityConfiguration.getUnits().intValue())
        .build();
  }

  private static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }
}
