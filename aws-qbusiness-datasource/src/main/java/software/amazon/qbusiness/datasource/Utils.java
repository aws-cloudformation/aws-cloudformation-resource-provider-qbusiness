package software.amazon.qbusiness.datasource;

import static software.amazon.qbusiness.common.SharedConstants.SERVICE_NAME_LOWER;

import java.util.Optional;

import org.json.JSONObject;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class Utils {

  // arn:${Partition}:qbusiness:${Region}:${Account}:application/${ApplicationId}/index/${IndexId}/data-source/${DataSourceId}
  private static final String DATA_SOURCE_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s/index/%s/data-source/%s";

  private Utils() {
  }

  public static String buildDataSourceArn(
      final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model
  ) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    var indexId = model.getIndexId();
    var dataSourceId = model.getDataSourceId();

    return buildDataSourceArn(partition, region, accountId, applicationId, indexId, dataSourceId);
  }

  private static String buildDataSourceArn(
      @NonNull String partition,
      @NonNull String region,
      @NonNull String accountId,
      @NonNull String applicationId,
      @NonNull String indexId,
      @NonNull String dataSourceId
  ) {
    return DATA_SOURCE_ARN_FORMAT.formatted(partition, region, accountId, applicationId, indexId, dataSourceId);
  }

  public static String primaryIdentifier(ResourceModel model) {
    return Optional.ofNullable(model)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");
  }
}
