package software.amazon.qbusiness.index;

import java.util.Locale;

public final class Constants {
  public static final String SERVICE_NAME = "QBusiness";
  public static final String API_GET_INDEX = "GetIndex";
  public static final String API_CREATE_INDEX = "CreateIndex";
  public static final String API_UPDATE_INDEX = "UpdateIndex";
  public static final String API_DELETE_INDEX = "DeleteIndex";
  public static final String SERVICE_NAME_LOWER = SERVICE_NAME.toLowerCase(Locale.ENGLISH);
  public static final String ENV_AWS_REGION = "AWS_REGION";

  private Constants() {
  }
}
