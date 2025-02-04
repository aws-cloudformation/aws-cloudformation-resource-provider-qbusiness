package software.amazon.qbusiness.permission;

import java.util.Locale;

public final class Constants {
  public static final String API_GET_POLICY = "GetPolicy";
  public static final String API_ASSOCIATE_PERMISSION = "AssociatePermission";
  public static final String API_DISASSOCIATE_PERMISSION = "DisassociatePermission";
  public static final String SERVICE_NAME = "QBusiness";
  public static final String SERVICE_NAME_LOWER = SERVICE_NAME.toLowerCase(Locale.ENGLISH);
  public static final String ENV_AWS_REGION = "AWS_REGION";

  private Constants() {
  }
}
