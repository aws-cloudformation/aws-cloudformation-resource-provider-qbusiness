package software.amazon.qbusiness.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import software.amazon.awssdk.services.qbusiness.model.CreateApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationRequest;
import software.amazon.awssdk.services.qbusiness.model.GetApplicationResponse;
import software.amazon.awssdk.services.qbusiness.model.ListApplicationsRequest;
import software.amazon.awssdk.services.qbusiness.model.ListApplicationsResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateApplicationRequest;
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
  static CreateApplicationRequest translateToCreateRequest(final String idempotentToken, final ResourceModel model) {
    // https://w.amazon.com/bin/view/AWS21/Design/Uluru/Onboarding_Guide/ModelingGuidelines#HRequiredWriteOnlyProperties
    if (model.getIdentityCenterInstanceArn() == null) {
      throw new CfnInvalidRequestException("IdentityCenterInstanceArn is required.");
    }
    return CreateApplicationRequest.builder()
        .clientToken(idempotentToken)
        .displayName(model.getDisplayName())
        .roleArn(model.getRoleArn())
        .identityCenterInstanceArn(model.getIdentityCenterInstanceArn())
        .description(model.getDescription())
        .encryptionConfiguration(toServiceEncryptionConfig(model.getEncryptionConfiguration()))
        .attachmentsConfiguration(toServiceAttachmentConfiguration(model.getAttachmentsConfiguration()))
        .tags(TagHelper.serviceTagsFromCfnTags(model.getTags()))
        .build();
  }

  /**
   * Request to read a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to read a resource
   */
  static GetApplicationRequest translateToReadRequest(final ResourceModel model) {
    return GetApplicationRequest.builder()
        .applicationId(model.getApplicationId())
        .build();
  }

  static ListTagsForResourceRequest translateToListTagsRequest(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var applicationArn = Utils.buildApplicationArn(request, model);

    return ListTagsForResourceRequest.builder()
        .resourceARN(applicationArn)
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   *
   * @param awsResponse the aws service get resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetApplicationResponse awsResponse) {
    return ResourceModel.builder()
        .displayName(awsResponse.displayName())
        .applicationId(awsResponse.applicationId())
        .applicationArn(awsResponse.applicationArn())
        .roleArn(awsResponse.roleArn())
        .identityCenterApplicationArn(awsResponse.identityCenterApplicationArn())
        .status(awsResponse.statusAsString())
        .description(awsResponse.description())
        .createdAt(instantToString(awsResponse.createdAt()))
        .updatedAt(instantToString(awsResponse.updatedAt()))
        .encryptionConfiguration(fromServiceEncryptionConfig(awsResponse.encryptionConfiguration()))
        .attachmentsConfiguration(fromServiceAttachmentConfiguration(awsResponse.attachmentsConfiguration()))
        .build();
  }

  static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
  }

  static EncryptionConfiguration fromServiceEncryptionConfig(
      software.amazon.awssdk.services.qbusiness.model.EncryptionConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return EncryptionConfiguration.builder()
        .kmsKeyId(serviceConfig.kmsKeyId())
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.EncryptionConfiguration toServiceEncryptionConfig(
      EncryptionConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.EncryptionConfiguration.builder()
        .kmsKeyId(modelConfig.getKmsKeyId())
        .build();
  }

  static AttachmentsConfiguration fromServiceAttachmentConfiguration(
      software.amazon.awssdk.services.qbusiness.model.AppliedAttachmentsConfiguration serviceConfig
  ) {
    if (serviceConfig == null) {
      return null;
    }

    return AttachmentsConfiguration.builder()
        .attachmentsControlMode(serviceConfig.attachmentsControlModeAsString())
        .build();
  }

  static software.amazon.awssdk.services.qbusiness.model.AttachmentsConfiguration toServiceAttachmentConfiguration(
      AttachmentsConfiguration modelConfig
  ) {
    if (modelConfig == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.AttachmentsConfiguration.builder()
        .attachmentsControlMode(modelConfig.getAttachmentsControlMode())
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

  /**
   * Request to delete a resource
   *
   * @param model resource model
   * @return DeleteApplicationRequest - the aws service request to delete a resource.
   */
  static DeleteApplicationRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteApplicationRequest.builder()
        .applicationId(model.getApplicationId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateApplicationRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateApplicationRequest.builder()
        .applicationId(model.getApplicationId())
        .displayName(model.getDisplayName())
        .description(model.getDescription())
        .roleArn(model.getRoleArn())
        .identityCenterInstanceArn(model.getIdentityCenterInstanceArn())
        .attachmentsConfiguration(toServiceAttachmentConfiguration(model.getAttachmentsConfiguration()))
        .build();
  }

  /**
   * Request to list resources
   *
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListApplicationsRequest translateToListRequest(final String nextToken) {
    return ListApplicationsRequest.builder()
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   *
   * @param serviceResponse the aws service get resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(final ListApplicationsResponse serviceResponse) {
    return serviceResponse.applications()
        .stream()
        .map(application -> ResourceModel.builder()
            .applicationId(application.applicationId())
            .displayName(application.displayName())
            .createdAt(instantToString(application.createdAt()))
            .updatedAt(instantToString(application.updatedAt()))
            .status(application.statusAsString())
            .build()
        )
        .toList();
  }

  /**
   * Request to add tags to a resource
   *
   * @param model resource model
   * @return TagResourceRequest the aws service request to create a resource
   */
  static TagResourceRequest tagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Map<String, String> addedTags) {
    var applicationArn = Utils.buildApplicationArn(request, model);

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
        .resourceARN(applicationArn)
        .tags(toTags)
        .build();
  }

  /**
   * Request to add tags to a resource
   *
   * @param model resource model
   * @return UntagResourceRequest the aws service request to create a resource
   */
  static UntagResourceRequest untagResourceRequest(
      final ResourceHandlerRequest<ResourceModel> request,
      final ResourceModel model,
      final Set<String> removedTags) {
    var applicationArn = Utils.buildApplicationArn(request, model);
    var tagsToRemove = Optional.ofNullable(removedTags)
        .filter(set -> !set.isEmpty())
        .orElse(null);

    return UntagResourceRequest.builder()
        .resourceARN(applicationArn)
        .tagKeys(tagsToRemove)
        .build();
  }
}
