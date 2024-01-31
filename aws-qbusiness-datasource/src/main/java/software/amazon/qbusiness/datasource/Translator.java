package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.datasource.translators.DocumentConverter.convertDocumentToMap;
import static software.amazon.qbusiness.datasource.translators.DocumentConverter.convertToMapToDocument;
import static software.amazon.qbusiness.datasource.translators.DocumentEnrichmentTranslator.fromServiceDocEnrichmentConf;
import static software.amazon.qbusiness.datasource.translators.DocumentEnrichmentTranslator.toServiceDocEnrichmentConf;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.services.qbusiness.model.CreateDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.DeleteDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceRequest;
import software.amazon.awssdk.services.qbusiness.model.GetDataSourceResponse;
import software.amazon.awssdk.services.qbusiness.model.ListDataSourcesRequest;
import software.amazon.awssdk.services.qbusiness.model.ListDataSourcesResponse;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UpdateDataSourceRequest;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class Translator {

  /**
   * Request to create a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateDataSourceRequest translateToCreateRequest(final String idempotencyToken, final ResourceModel model) {
    return CreateDataSourceRequest.builder()
        .clientToken(idempotencyToken)
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .displayName(model.getDisplayName())
        .description(model.getDescription())
        .roleArn(model.getRoleArn())
        .syncSchedule(model.getSyncSchedule())
        .tags(TagHelper.serviceTagsFromCfnTags(model.getTags()))
        .vpcConfiguration(toServiceDataSourceVpcConfiguration(model.getVpcConfiguration()))
        .configuration(convertToMapToDocument(model.getConfiguration()))
        .documentEnrichmentConfiguration(toServiceDocEnrichmentConf(model.getDocumentEnrichmentConfiguration()))
        .build();
  }

  /**
   * Request to read a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static GetDataSourceRequest translateToReadRequest(final ResourceModel model) {
    return GetDataSourceRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .dataSourceId(model.getDataSourceId())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   *
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final GetDataSourceResponse awsResponse) {
    return ResourceModel.builder()
        .applicationId(awsResponse.applicationId())
        .indexId(awsResponse.indexId())
        .dataSourceId(awsResponse.dataSourceId())
        .displayName(awsResponse.displayName())
        .description(awsResponse.description())
        .createdAt(instantToString(awsResponse.createdAt()))
        .updatedAt(instantToString(awsResponse.updatedAt()))
        .roleArn(awsResponse.roleArn())
        .syncSchedule(awsResponse.syncSchedule())
        .type(awsResponse.type())
        .status(awsResponse.statusAsString())
        .vpcConfiguration(fromServiceDataSourceVpcConfiguration(awsResponse.vpcConfiguration()))
        .configuration(convertDocumentToMap(awsResponse.configuration()))
        .documentEnrichmentConfiguration(fromServiceDocEnrichmentConf(awsResponse.documentEnrichmentConfiguration()))
        .build();
  }

  static DataSourceVpcConfiguration fromServiceDataSourceVpcConfiguration(
      software.amazon.awssdk.services.qbusiness.model.DataSourceVpcConfiguration maybeServiceConf
  ) {
    return Optional.ofNullable(maybeServiceConf)
        .map(serviceConf -> DataSourceVpcConfiguration.builder()
            .subnetIds(serviceConf.subnetIds())
            .securityGroupIds(serviceConf.securityGroupIds())
            .build())
        .orElse(null);
  }

  static software.amazon.awssdk.services.qbusiness.model.DataSourceVpcConfiguration toServiceDataSourceVpcConfiguration(
      DataSourceVpcConfiguration modelData
  ) {
    if (modelData == null) {
      return null;
    }

    return software.amazon.awssdk.services.qbusiness.model.DataSourceVpcConfiguration.builder()
        .subnetIds(modelData.getSubnetIds())
        .securityGroupIds(modelData.getSecurityGroupIds())
        .build();
  }

  public static String instantToString(Instant instant) {
    return Optional.ofNullable(instant)
        .map(Instant::toString)
        .orElse(null);
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
    var dataSourceArn = Utils.buildDataSourceArn(request, model);
    return ListTagsForResourceRequest.builder()
        .resourceARN(dataSourceArn)
        .build();
  }

  /**
   * Request to delete a resource
   *
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteDataSourceRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteDataSourceRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .dataSourceId(model.getDataSourceId())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   *
   * @param model resource model
   * @return UpdateDataSourceRequest the aws service request to modify a resource
   */
  static UpdateDataSourceRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateDataSourceRequest.builder()
        .applicationId(model.getApplicationId())
        .indexId(model.getIndexId())
        .dataSourceId(model.getDataSourceId())
        .description(model.getDescription())
        .displayName(model.getDisplayName())
        .roleArn(model.getRoleArn())
        .syncSchedule(model.getSyncSchedule())
        .vpcConfiguration(toServiceDataSourceVpcConfiguration(model.getVpcConfiguration()))
        .configuration(convertToMapToDocument(model.getConfiguration()))
        .documentEnrichmentConfiguration(toServiceDocEnrichmentConf(model.getDocumentEnrichmentConfiguration()))
        .build();
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
   * @return awsRequest the aws service request to list resources within aws account
   */
  static ListDataSourcesRequest translateToListRequest(
      ResourceModel resourceModel,
      final String nextToken
  ) {
    return ListDataSourcesRequest.builder()
        .applicationId(resourceModel.getApplicationId())
        .indexId(resourceModel.getIndexId())
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   *
   * @param applicationId   - The id of the application to list data sources.
   * @param indexId         - The parent index id.
   * @param serviceResponse - the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListResponse(
      final String applicationId,
      final String indexId,
      final ListDataSourcesResponse serviceResponse) {
    return streamOfOrEmpty(serviceResponse.dataSources())
        .map(dataSource -> ResourceModel.builder()
            .applicationId(applicationId)
            .indexId(indexId)
            .dataSourceId(dataSource.dataSourceId())
            .build()
        )
        .toList();
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection).stream()
        .flatMap(Collection::stream);
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
    var dataSourceArn = Utils.buildDataSourceArn(request, model);

    List<software.amazon.awssdk.services.qbusiness.model.Tag> toTags = Optional.ofNullable(addedTags)
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
        .resourceARN(dataSourceArn)
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
    var dataSourceArn = Utils.buildDataSourceArn(request, model);
    var tagsToRemove = Optional.ofNullable(removedTags)
        .filter(set -> !set.isEmpty())
        .orElse(null);

    return UntagResourceRequest.builder()
        .resourceARN(dataSourceArn)
        .tagKeys(tagsToRemove)
        .build();
  }

}
