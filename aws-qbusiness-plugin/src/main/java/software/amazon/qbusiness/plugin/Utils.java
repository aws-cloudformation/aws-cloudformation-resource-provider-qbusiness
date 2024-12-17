package software.amazon.qbusiness.plugin;

import static software.amazon.qbusiness.plugin.Constants.SERVICE_NAME_LOWER;

import java.util.Locale;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class Utils {
  // arn:${partition}:qbusiness:${region}:${Account}:application/${ApplicationId}/plugin/${pluginId}
  private static final String PLUGIN_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s/plugin/%s";

  private Utils() {
  }

  public static String buildPluginArn(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    var pluginId = model.getPluginId();
    return buildPluginArn(partition, region, accountId, applicationId, pluginId);
  }

  private static String buildPluginArn(
      @NonNull final String partition,
      @NonNull final String region,
      @NonNull final String accountId,
      @NonNull final String applicationId,
      @NonNull final String pluginId) {
    return PLUGIN_ARN_FORMAT.formatted(partition, region, accountId, applicationId, pluginId).toLowerCase(Locale.ENGLISH);
  }
}
