package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.dataaccessor.converter.ActionConfigurationConverter.fromServiceActionConfigurations;
import static software.amazon.qbusiness.dataaccessor.converter.ActionConfigurationConverter.toServiceActionConfigurations;

import com.google.common.collect.Lists;
import java.time.Instant;
import software.amazon.awssdk.awscore.AwsResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorResponse;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsRequest;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorRequest;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  public static software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorRequest translateToCreateRequest(final String idempotentToken, final ResourceModel model) {
    return software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorRequest.builder()
        .clientToken(idempotentToken)
        .displayName(model.getDisplayName())
        .applicationId(model.getApplicationId())
        .principal(model.getPrincipal())
        .actionConfigurations(toServiceActionConfigurations(model.getActionConfigurations()))
        .tags(TagHelper.serviceTagsFromCfnTags(model.getTags()))
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws git service request to describe a resource
   */
  static GetDataAccessorRequest translateToReadRequest(final ResourceModel model) {
    return GetDataAccessorRequest.builder()
        .applicationId(model.getApplicationId())
        .dataAccessorId(model.getDataAccessorId())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param response the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetDataAccessorResponse response) {
    return ResourceModel.builder()
        .applicationId(response.applicationId())
        .dataAccessorId(response.dataAccessorId())
        .dataAccessorArn(response.dataAccessorArn())
        .displayName(response.displayName())
        .idcApplicationArn(response.idcApplicationArn())
        .principal(response.principal())
        .actionConfigurations(fromServiceActionConfigurations(response.actionConfigurations()))
        .createdAt(instantToString(response.createdAt()))
        .updatedAt(instantToString(response.updatedAt()))
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteDataAccessorRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteDataAccessorRequest.builder()
        .applicationId(model.getApplicationId())
        .dataAccessorId(model.getDataAccessorId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateDataAccessorRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateDataAccessorRequest.builder()
        .applicationId(model.getApplicationId())
        .dataAccessorId(model.getDataAccessorId())
        .displayName(model.getDisplayName())
        .actionConfigurations(toServiceActionConfigurations(model.getActionConfigurations()))
        .build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListDataAccessorsRequest translateToListRequest(ResourceModel model, final String nextToken) {
    return ListDataAccessorsRequest.builder()
        .applicationId(model.getApplicationId())
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   *
   * @param response the aws service get resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(final ListDataAccessorsResponse response, final String applicationId) {
    return response.dataAccessors()
        .stream()
        .map(result -> ResourceModel.builder()
            .applicationId(applicationId)
            .displayName(result.displayName())
            .dataAccessorId(result.dataAccessorId())
            .idcApplicationArn(result.idcApplicationArn())
            .principal(result.principal())
            .createdAt(instantToString(result.createdAt()))
            .updatedAt(instantToString(result.updatedAt()))
            .build()
        )
        .toList();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param awsResponse the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final AwsResponse awsResponse) {
    // e.g. https://github.com/aws-cloudformation/aws-cloudformation-resource-providers-logs/blob/2077c92299aeb9a68ae8f4418b5e932b12a8b186/aws-logs-loggroup/src/main/java/com/aws/logs/loggroup/Translator.java#L75-L82
    return streamOfOrEmpty(Lists.newArrayList())
        .map(resource -> ResourceModel.builder()
            // include only primary identifier
            .build())
        .collect(Collectors.toList());
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  /**
   * Request to add tags to a resource
   * @return awsRequest the aws service request to create a resource
   */
  static TagResourceRequest tagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Map<String, String> addedTags) {
    var dataAccessorArn = Utils.buildDataAccessorArn(request, model);

    List<software.amazon.awssdk.services.qbusiness.model.Tag> toTags = Optional.ofNullable(addedTags)
        .map(Map::entrySet)
        .map(pairs -> pairs.stream()
            .map(pair -> software.amazon.awssdk.services.qbusiness.model.Tag.builder()
                .key(pair.getKey())
                .value(pair.getValue())
                .build()
            )
            .toList()
        )
        .filter(list -> !list.isEmpty())
        .orElse(null);
    return TagResourceRequest.builder()
        .resourceARN(dataAccessorArn)
        .tags(toTags)
        .build();
  }

  /**
   * Request to add tags to a resource
   * @return awsRequest the aws service request to create a resource
   */
  static UntagResourceRequest untagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Set<String> removedTags) {
    var dataAccessorArn = Utils.buildDataAccessorArn(request, model);
    var tagsToRemove = Optional.ofNullable(removedTags)
        .filter(set -> !set.isEmpty())
        .orElse(null);

    return UntagResourceRequest.builder()
        .resourceARN(dataAccessorArn)
        .tagKeys(tagsToRemove)
        .build();
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
    var dataAccessorArn = model.getDataAccessorArn();
    return ListTagsForResourceRequest.builder()
        .resourceARN(dataAccessorArn)
        .build();
  }

  static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }
}
