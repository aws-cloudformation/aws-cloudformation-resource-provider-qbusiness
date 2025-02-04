package software.amazon.qbusiness.permission;

import software.amazon.awssdk.services.qbusiness.model.AssociatePermissionRequest;
import software.amazon.awssdk.services.qbusiness.model.DisassociatePermissionRequest;
import software.amazon.awssdk.services.qbusiness.model.GetPolicyRequest;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static AssociatePermissionRequest translateToCreateRequest(final ResourceModel model) {
    return AssociatePermissionRequest.builder()
        .applicationId(model.getApplicationId())
        .statementId(model.getStatementId())
        .actions(model.getActions())
        .principal(model.getPrincipal())
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static GetPolicyRequest translateToReadRequest(final ResourceModel model) {
    return GetPolicyRequest.builder()
        .applicationId(model.getApplicationId())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DisassociatePermissionRequest translateToDeleteRequest(final ResourceModel model) {
    return DisassociatePermissionRequest.builder()
        .applicationId(model.getApplicationId())
        .statementId(model.getStatementId())
        .build();
  }
}
