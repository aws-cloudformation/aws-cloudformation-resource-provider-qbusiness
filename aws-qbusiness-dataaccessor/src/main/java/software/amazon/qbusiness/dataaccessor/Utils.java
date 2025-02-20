package software.amazon.qbusiness.dataaccessor;

import static software.amazon.qbusiness.common.SharedConstants.SERVICE_NAME_LOWER;

import java.util.Locale;
import java.util.Optional;

import org.json.JSONObject;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


public class Utils {

  // arn:${Partition}:qbusiness:${Region}:${Account}:application/${ApplicationId}/data-accessor/${RetrieverId}
  private static final String DATA_ACCESSOR_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s/data-accessor/%s";

  private Utils() {
  }

  public static String buildDataAccessorArn(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    var dataAccessorId = model.getDataAccessorId();
    return buildDataAccessorArn(partition, region, accountId, applicationId, dataAccessorId);
  }

  public static String primaryIdentifier(ResourceModel model) {
    return Optional.ofNullable(model)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");
  }

  private static String buildDataAccessorArn(
      @NonNull String partition,
      @NonNull String region,
      @NonNull String accountId,
      @NonNull String applicationId,
      @NonNull String retrieverId) {
    return DATA_ACCESSOR_ARN_FORMAT.formatted(partition, region, accountId, applicationId, retrieverId).toLowerCase(Locale.ENGLISH);
  }
}
