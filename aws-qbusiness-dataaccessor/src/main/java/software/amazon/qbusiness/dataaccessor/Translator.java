package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.dataaccessor.converter.ActionConfigurationConverter.fromServiceActionConfigurations;
import static software.amazon.qbusiness.dataaccessor.converter.ActionConfigurationConverter.toServiceActionConfigurations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import software.amazon.awssdk.services.qbusiness.model.DeleteDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataAccessorResponse;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsRequest;
import software.amazon.awssdk.services.qbusiness.model.ListDataAccessorsResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataAccessorRequest;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.qbusiness.common.TagUtils;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  /**
   * Request to create a resource
   * @param request resource handler request.
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  public static software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorRequest translateToCreateRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model
  ) {
    return software.amazon.awssdk.services.qbusiness.model.CreateDataAccessorRequest.builder()
        .clientToken(request.getClientRequestToken())
        .displayName(model.getDisplayName())
        .applicationId(model.getApplicationId())
        .principal(model.getPrincipal())
        .actionConfigurations(toServiceActionConfigurations(model.getActionConfigurations()))
        .tags(TagUtils.mergeCreateHandlerTagsToSdkTags(request, model))
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
