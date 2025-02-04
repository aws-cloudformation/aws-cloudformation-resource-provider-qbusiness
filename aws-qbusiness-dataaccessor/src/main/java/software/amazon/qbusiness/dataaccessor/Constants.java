package software.amazon.qbusiness.dataaccessor;

import java.util.Locale;

public final class Constants {
  public static final String API_GET_DATA_ACCESSOR = "GetDataAccessor";
  public static final String API_CREATE_DATA_ACCESSOR = "CreateDataAccessor";
  public static final String API_DELETE_DATA_ACCESSOR = "DeleteDataAccessor";
  public static final String API_UPDATE_DATA_ACCESSOR = "UpdateDataAccessor";
  public static final String SERVICE_NAME = "QBusiness";
  public static final String SERVICE_NAME_LOWER = SERVICE_NAME.toLowerCase(Locale.ENGLISH);
  public static final String ENV_AWS_REGION = "AWS_REGION";

  private Constants() {
  }
}
