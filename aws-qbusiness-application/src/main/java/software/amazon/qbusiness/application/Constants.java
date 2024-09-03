package software.amazon.qbusiness.application;

import java.util.Locale;

public final class Constants {
  public static final String SERVICE_NAME = "QBusiness";
  public static final String API_GET_APPLICATION = "GetApplication";
  public static final String API_CREATE_APPLICATION = "CreateApplication";
  public static final String API_DELETE_APPLICATION = "DeleteApplication";
  public static final String API_UPDATE_APPLICATION = "UpdateApplication";
  public static final String SERVICE_NAME_LOWER = SERVICE_NAME.toLowerCase(Locale.ENGLISH);
  public static final String ENV_AWS_REGION = "AWS_REGION";

  public static final String AUTOSUBSCRIBE_FIELD_VALIDATION_ERROR =
      "AutoSubscriptionConfiguration must be ENABLED with a default subscription tier for %s identity type.";

  private Constants() {
  }
}
