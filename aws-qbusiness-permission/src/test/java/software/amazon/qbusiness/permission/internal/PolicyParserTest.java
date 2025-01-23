package software.amazon.qbusiness.permission.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.qbusiness.permission.ResourceModel;

class PolicyParserTest {
  private static final String APPLICATION_ID = "app-123";
  private static final String STATEMENT_ID = "test-statement";
  private static final String PRINCIPAL_ARN = "arn:aws:iam::615299774811:role/test-role";
  private static final String ACTION = "qbusiness:GetRelevantContent";
  private static final String RESOURCE = "arn:aws:qbusiness:us-west-2:933142937839:application/test-app";

  private String validPolicyJson;

  @BeforeEach
  void setUp() {
    validPolicyJson = String.format("""
            {
                "Version": "2012-10-17",
                "Statement": [{
                    "Sid": "%s",
                    "Effect": "Allow",
                    "Principal": {
                        "AWS": "%s"
                    },
                    "Action": "%s",
                    "Resource": ["%s"]
                }]
            }
            """, STATEMENT_ID, PRINCIPAL_ARN, ACTION, RESOURCE);
  }

  @Test
  void testGetPermissionModelsFromPolicy_ValidPolicy() {
    List<ResourceModel> result = PolicyParser.getPermissionModelsFromPolicy(validPolicyJson, APPLICATION_ID);

    assertEquals(1, result.size());
    ResourceModel model = result.get(0);
    assertEquals(APPLICATION_ID, model.getApplicationId());
    assertEquals(STATEMENT_ID, model.getStatementId());
    assertEquals(PRINCIPAL_ARN, model.getPrincipal());
    assertEquals(Collections.singletonList(ACTION), model.getActions());
  }

  @Test
  void testGetPermissionModelsFromPolicy_MultiplePrincipals() {
    String policyWithMultiplePrincipals = """
            {
                "Version": "2012-10-17",
                "Statement": [{
                    "Sid": "test-statement",
                    "Effect": "Allow",
                    "Principal": {
                        "AWS": ["arn:aws:iam::123:role/role1", "arn:aws:iam::123:role/role2"]
                    },
                    "Action": "qbusiness:GetRelevantContent",
                    "Resource": ["test-resource"]
                }]
            }
            """;

    assertThrows(IllegalStateException.class,
        () -> PolicyParser.getPermissionModelsFromPolicy(policyWithMultiplePrincipals, APPLICATION_ID));
  }

  @Test
  void testGetPermissionModelsFromPolicy_MultipleActions() {
    String policyWithMultipleActions = """
            {
                "Version": "2012-10-17",
                "Statement": [{
                    "Sid": "test-statement",
                    "Effect": "Allow",
                    "Principal": {
                        "AWS": "arn:aws:iam::123:role/role1"
                    },
                    "Action": ["qbusiness:Action1", "qbusiness:Action2"],
                    "Resource": ["test-resource"]
                }]
            }
            """;

    List<ResourceModel> result = PolicyParser.getPermissionModelsFromPolicy(policyWithMultipleActions, APPLICATION_ID);

    assertEquals(1, result.size());
    assertEquals(Arrays.asList("qbusiness:Action1", "qbusiness:Action2"), result.get(0).getActions());
  }

  @Test
  void testGetStatementFromPolicy_ExistingStatement() {
    Optional<ResourceModel> result = PolicyParser.getStatementFromPolicy(
        validPolicyJson, STATEMENT_ID, APPLICATION_ID);

    assertTrue(result.isPresent());
    assertEquals(STATEMENT_ID, result.get().getStatementId());
  }

  @Test
  void testGetStatementFromPolicy_NonExistingStatement() {
    Optional<ResourceModel> result = PolicyParser.getStatementFromPolicy(
        validPolicyJson, "non-existing-id", APPLICATION_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void testGetStatementFromPolicy_NullPolicy() {
    assertThrows(NullPointerException.class,
        () -> PolicyParser.getStatementFromPolicy(null, STATEMENT_ID, APPLICATION_ID));
  }

  @Test
  void testGetStatementFromPolicy_NullStatementId() {
    assertThrows(NullPointerException.class,
        () -> PolicyParser.getStatementFromPolicy(validPolicyJson, null, APPLICATION_ID));
  }

  @Test
  void testGetPermissionModelsFromPolicy_EmptyPolicy() {
    String emptyPolicy = """
            {
                "Version": "2012-10-17",
                "Statement": []
            }
            """;

    List<ResourceModel> result = PolicyParser.getPermissionModelsFromPolicy(emptyPolicy, APPLICATION_ID);
    assertTrue(result.isEmpty());
  }
}
