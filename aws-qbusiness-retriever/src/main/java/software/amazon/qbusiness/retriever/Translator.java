package software.amazon.qbusiness.retriever;

import static java.util.stream.Collectors.toList;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import software.amazon.awssdk.services.qbusiness.model.CreateRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverRequest;
import software.amazon.awssdk.services.qbusiness.model.GetRetrieverResponse;
import software.amazon.awssdk.services.qbusiness.model.ListRetrieversRequest;
import software.amazon.awssdk.services.qbusiness.model.ListRetrieversResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateRetrieverRequest;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
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
  static CreateRetrieverRequest translateToCreateRequest(final String idempotentToken, final ResourceModel model) {
    return CreateRetrieverRequest.builder()
        .applicationId(model.getApplicationId())
        .type(model.getType())
        .displayName(model.getDisplayName())
        .configuration(toServiceRetrieverConfiguration(model.getConfiguration()))
        .roleArn(model.getRoleArn())
        .clientToken(idempotentToken)
        .tags(TagHelper.serviceTagsFromCfnTags(model.getTags()))
        .build();
  }

  /**
   * Request to read a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static GetRetrieverRequest translateToReadRequest(final ResourceModel model) {
    return GetRetrieverRequest.builder()
        .applicationId(model.getApplicationId())
        .retrieverId(model.getRetrieverId())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   *
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetRetrieverResponse awsResponse) {
    return ResourceModel.builder()
        .applicationId(awsResponse.applicationId())
        .retrieverId(awsResponse.retrieverId())
        .type(awsResponse.typeAsString())
        .status(awsResponse.statusAsString())
        .displayName(awsResponse.displayName())
        .configuration(fromServiceRetrieverConfiguration(awsResponse.configuration()))
        .roleArn(awsResponse.roleArn())
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
  static DeleteRetrieverRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteRetrieverRequest.builder()
        .applicationId(model.getApplicationId())
        .retrieverId(model.getRetrieverId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateRetrieverRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateRetrieverRequest.builder()
        .applicationId(model.getApplicationId())
        .retrieverId(model.getRetrieverId())
        .configuration(toServiceRetrieverConfiguration(model.getConfiguration()))
        .displayName(model.getDisplayName())
        .roleArn(model.getRoleArn())
        .build();
  }

  /**
   * Request to list resources
   *
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListRetrieversRequest translateToListRequest(ResourceModel resourceModel, final String nextToken) {
    return ListRetrieversRequest.builder()
        .applicationId(resourceModel.getApplicationId())
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   *
   * @param serviceResponse the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(final ListRetrieversResponse serviceResponse) {
    return serviceResponse.retrievers()
        .stream()
        .map(retriever -> ResourceModel.builder()
            .applicationId(retriever.applicationId())
            .retrieverId(retriever.retrieverId())
            .build()
        )
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
    var retrieverArn = Utils.buildRetrieverArn(request, model);

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
        .resourceARN(retrieverArn)
        .tags(toTags)
        .build();
  }

  /**
   * Request to add tags to a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static UntagResourceRequest untagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Set<String> removedTags) {
    var retrieverArn = Utils.buildRetrieverArn(request, model);
    var tagsToRemove = Optional.ofNullable(removedTags)
        .filter(set -> !set.isEmpty())
        .orElse(null);

    return UntagResourceRequest.builder()
        .resourceARN(retrieverArn)
        .tagKeys(tagsToRemove)
        .build();
  }

  static ListTagsForResourceRequest translateToListTagsRequest(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var retrieverArn = Utils.buildRetrieverArn(request, model);

    return ListTagsForResourceRequest.builder()
        .resourceARN(retrieverArn)
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

  static software.amazon.awssdk.services.qbusiness.model.RetrieverConfiguration toServiceRetrieverConfiguration(
      RetrieverConfiguration modelRetrieverConfiguration
  ) {
    if (modelRetrieverConfiguration == null) {
      return null;
    }

    if (modelRetrieverConfiguration.getKendraIndexConfiguration() == null && modelRetrieverConfiguration.getNativeIndexConfiguration() == null) {
      throw new CfnInvalidRequestException("Neither index nor native configuration is provided.");
    }

    if (modelRetrieverConfiguration.getKendraIndexConfiguration() != null) {
      return software.amazon.awssdk.services.qbusiness.model.RetrieverConfiguration.builder()
          .kendraIndexConfiguration(toServiceKendraIndexConfiguration(modelRetrieverConfiguration.getKendraIndexConfiguration()))
          .build();
    }

    return software.amazon.awssdk.services.qbusiness.model.RetrieverConfiguration.builder()
        .nativeIndexConfiguration(toServiceNativeIndexConfiguration(modelRetrieverConfiguration.getNativeIndexConfiguration()))
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.KendraIndexConfiguration toServiceKendraIndexConfiguration(
      KendraIndexConfiguration modelKendraIndexConfiguration
  ) {
    if (modelKendraIndexConfiguration == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.KendraIndexConfiguration.builder()
        .indexId(modelKendraIndexConfiguration.getIndexId())
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.NativeIndexConfiguration toServiceNativeIndexConfiguration(
      NativeIndexConfiguration modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.NativeIndexConfiguration.builder()
        .indexId(modelData.getIndexId())
        .build();
  }

  static RetrieverConfiguration fromServiceRetrieverConfiguration(
      software.amazon.awssdk.services.qbusiness.model.RetrieverConfiguration serviceRetrieverConfiguration
  ) {
    if (serviceRetrieverConfiguration == null) {
      return null;
    }

    return RetrieverConfiguration.builder()
        .kendraIndexConfiguration(fromServiceKendraIndexConfiguration(serviceRetrieverConfiguration.kendraIndexConfiguration()))
        .build();
  }

  static KendraIndexConfiguration fromServiceKendraIndexConfiguration(
      software.amazon.awssdk.services.qbusiness.model.KendraIndexConfiguration serviceKendraIndexConfiguration
  ) {
    if (serviceKendraIndexConfiguration == null) {
      return null;
    }

    return KendraIndexConfiguration.builder()
        .indexId(serviceKendraIndexConfiguration.indexId())
        .build();
  }

  static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }
}
