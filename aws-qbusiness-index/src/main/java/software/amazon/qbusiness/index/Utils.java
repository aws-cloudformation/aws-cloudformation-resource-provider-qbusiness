package software.amazon.qbusiness.index;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Locale;
import java.util.Optional;

import static software.amazon.qbusiness.index.Constants.SERVICE_NAME_LOWER;

import org.json.JSONObject;

public class Utils {

  // arn:${Partition}:qbusiness:${Region}:${Account}:application/${ApplicationId}/index/${IndexId}
  private static final String INDEX_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s/index/%s";

  private Utils() {
  }

  public static String buildIndexArn(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    var indexId = model.getIndexId();
    return buildIndexArn(partition, region, accountId, applicationId, indexId);
  }

  private static String buildIndexArn(
      @NonNull final String partition,
      @NonNull final String region,
      @NonNull final String accountId,
      @NonNull final String applicationId,
      @NonNull final String indexId) {
    return INDEX_ARN_FORMAT.formatted(partition, region, accountId, applicationId, indexId).toLowerCase(Locale.ENGLISH);
  }

  public static String primaryIdentifier(ResourceModel model) {
    return Optional.ofNullable(model)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");
  }
}
