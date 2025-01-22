package software.amazon.qbusiness.common;

import software.amazon.awssdk.services.qbusiness.model.AccessDeniedException;
import software.amazon.awssdk.services.qbusiness.model.ConflictException;
import software.amazon.awssdk.services.qbusiness.model.ResourceNotFoundException;
import software.amazon.awssdk.services.qbusiness.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.qbusiness.model.ThrottlingException;
import software.amazon.awssdk.services.qbusiness.model.ValidationException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.StdCallbackContext;

public class ErrorUtils {

  public static  <RType, Ctx extends StdCallbackContext> ProgressEvent<RType, Ctx> handleError(
      RType resourceModel,
      String primaryIdentifier,
      Exception error,
      Ctx context,
      Logger logger,
      String typeName,
      String apiName
  ) {
    logger.log("[ERROR] Failed Request: %s. Error Message: %s".formatted(apiName, error.getMessage()));
    BaseHandlerException cfnException;

    if (error instanceof ResourceNotFoundException) {
      cfnException = new CfnNotFoundException(typeName, primaryIdentifier, error);
    } else if (error instanceof ValidationException || error instanceof CfnInvalidRequestException) {
      cfnException = new CfnInvalidRequestException(error);
    } else if (error instanceof ThrottlingException) {
      cfnException = new CfnThrottlingException(apiName, error);
    } else if (error instanceof ConflictException) {
      cfnException = new CfnResourceConflictException(error);
    } else if (error instanceof AccessDeniedException) {
      cfnException = new CfnAccessDeniedException(apiName, error);
    } else if (error instanceof ServiceQuotaExceededException) {
      cfnException = new CfnServiceLimitExceededException(error);
    } else {
      cfnException = new CfnGeneralServiceException(error);
    }

    return ProgressEvent.failed(resourceModel, context, cfnException.getErrorCode(), error.getMessage());
  }
}
