package software.amazon.qbusiness.retriever;

import lombok.NonNull;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Locale;
import java.util.Optional;

import static software.amazon.qbusiness.common.SharedConstants.SERVICE_NAME_LOWER;

import org.json.JSONObject;

public class Utils {

  // arn:${Partition}:qbusiness:${Region}:${Account}:application/${ApplicationId}/retriever/${RetrieverId}
  private static final String RETRIEVER_ARN_FORMAT = "arn:%s:" + SERVICE_NAME_LOWER + ":%s:%s:application/%s/retriever/%s";

  private Utils() {
  }

  public static String buildRetrieverArn(final ResourceHandlerRequest<ResourceModel> request, final ResourceModel model) {
    var partition = request.getAwsPartition();
    var region = request.getRegion();
    var accountId = request.getAwsAccountId();
    var applicationId = model.getApplicationId();
    var retrieverId = model.getRetrieverId();
    return buildRetrieverArn(partition, region, accountId, applicationId, retrieverId);
  }

  private static String buildRetrieverArn(
      @NonNull String partition,
      @NonNull String region,
      @NonNull String accountId,
      @NonNull String applicationId,
      @NonNull String retrieverId) {
    return RETRIEVER_ARN_FORMAT.formatted(partition, region, accountId, applicationId, retrieverId).toLowerCase(Locale.ENGLISH);
  }

  public static String primaryIdentifier(ResourceModel model) {
    return Optional.ofNullable(model)
        .map(ResourceModel::getPrimaryIdentifier)
        .map(JSONObject::toString)
        .orElse("");
  }
}
