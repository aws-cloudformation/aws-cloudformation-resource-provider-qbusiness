package software.amazon.qbusiness.permission.internal;

import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.PolicyReaderOptions;
import com.amazonaws.auth.policy.Statement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import software.amazon.qbusiness.permission.ResourceModel;

public class PolicyParser {
  /**
   * This method parses the output policy of GetPolicy into a list of permission ResourceModel
   * Example:
   * {
   *     "policy": {
   *         "Version": "2012-10-17",
   *         "Statement": [{
   *             "Sid": "SAMTest-GetRelevantContent-access",
   *             "Effect": "Allow",
   *             "Principal": {
   *                 "AWS": "arn:aws:iam::615299774811:role/cross-auth-role"
   *             },
   *             "Action": "qbusiness:GetRelevantContent",
   *             "Resource": ["arn:aws:qbusiness:us-west-2:933142937839:application/0c822c4f-b748-4694-9729-45f70cd0cfc9"]
   *         }]
   *     }
   * }
   * @param policy
   * @return a list of ResourceModel for Qbusiness::Permission
   */
  public static List<ResourceModel> getPermissionModelsFromPolicy(final String policy, final String applicationId) {
    final PolicyReaderOptions policyReaderOptions = new PolicyReaderOptions().withStripAwsPrincipalIdHyphensEnabled(false);
    final Policy parsedPolicy = Policy.fromJson(policy, policyReaderOptions);
    return parsedPolicy.getStatements()
        .stream()
        .map(statement -> getPermissionModelFromStatement(statement, applicationId))
        .collect(Collectors.toList());
  }

  public static Optional<ResourceModel> getStatementFromPolicy(@NonNull final String policy, @NonNull final String statementId, final String applicationId) {
    final List<ResourceModel> policyModels = getPermissionModelsFromPolicy(policy, applicationId);

    return policyModels.stream()
        .filter(model -> statementId.equals(model.getStatementId()))
        .findAny();
  }

  private static ResourceModel getPermissionModelFromStatement(final Statement statement, final String applicationId) {
    final String id = statement.getId();
    final List<String> actions = statement.getActions().stream().map(Action::getActionName).toList();
    // FE model accepts a single Principal per statement
    if (statement.getPrincipals().size() != 1 ) {
      throw new IllegalStateException(String.format("getPolicy returned statement with unexpected number of principals: %s, only %d allowed", statement.getPrincipals(), 1));
    }
    final String principal = statement.getPrincipals().get(0).getId();

    return ResourceModel.builder()
        .applicationId(applicationId)
        .statementId(id)
        .actions(actions)
        .principal(principal)
        .build();
  }

}
