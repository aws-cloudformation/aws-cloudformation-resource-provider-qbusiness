package software.amazon.qbusiness.application;

import static software.amazon.qbusiness.application.Constants.SERVICE_NAME_LOWER;

import java.util.Locale;
import java.util.Optional;

import org.json.JSONObject;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class Utils {

  // arn:${Partition}:qbusiness:${Region}:${Account}:application/${ApplicationId}
  private static final String APPLICATION_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s";

  private Utils() {
  }

  public static String buildApplicationArn(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    return buildApplicationArn(partition, region, accountId, applicationId);
  }

  private static String buildApplicationArn(
      @NonNull String partition,
      @NonNull String region,
      @NonNull String accountId,
      @NonNull String applicationId) {
    return APPLICATION_ARN_FORMAT.formatted(partition, region, accountId, applicationId).toLowerCase(Locale.ENGLISH);
  }

  public static String primaryIdentifier(ResourceModel model) {
    return Optional.ofNullable(model)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");
  }

}
