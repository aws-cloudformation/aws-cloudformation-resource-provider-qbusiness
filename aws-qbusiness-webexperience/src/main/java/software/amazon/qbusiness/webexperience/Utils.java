package software.amazon.qbusiness.webexperience;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Locale;
import java.util.Optional;

import static software.amazon.qbusiness.webexperience.Constants.SERVICE_NAME_LOWER;

import org.json.JSONObject;

public class Utils {

  // arn:${Partition}:qbusiness:${Region}:${Account}:application/${ApplicationId}/web-experience/${WebExperienceId}
  private static final String WEB_EXPERIENCE_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s/web-experience/%s";

  private Utils() {
  }

  public static String buildWebExperienceArn(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    var webExperienceId = model.getWebExperienceId();
    return buildWebExperienceArn(partition, region, accountId, applicationId, webExperienceId);
  }

  private static String buildWebExperienceArn(
      @NonNull final String partition,
      @NonNull final String region,
      @NonNull final String accountId,
      @NonNull final String applicationId,
      @NonNull final String webExperienceId) {
    return WEB_EXPERIENCE_ARN_FORMAT.formatted(partition, region, accountId, applicationId, webExperienceId).toLowerCase(Locale.ENGLISH);
  }

  public static String primaryIdentifier(ResourceModel model) {
    return Optional.ofNullable(model)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");
  }
}
