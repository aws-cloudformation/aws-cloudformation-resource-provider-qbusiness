package software.amazon.qbusiness.datasource;

import java.util.List;

public class TagHelper {

  public static List<software.amazon.qbusiness.datasource.Tag> cfnTagsFromServiceTags(
      List<software.amazon.awssdk.services.qbusiness.model.Tag> serviceTags
  ) {
    return serviceTags.stream()
        .map(serviceTag -> new software.amazon.qbusiness.datasource.Tag(serviceTag.key(), serviceTag.value()))
        .toList();
  }
}
