package software.amazon.qbusiness.plugin;

import java.util.List;

public class TagHelper {

  public static List<software.amazon.qbusiness.plugin.Tag> cfnTagsFromServiceTags(
      List<software.amazon.awssdk.services.qbusiness.model.Tag> serviceTags
  ) {
    return serviceTags.stream()
        .map(serviceTag -> new software.amazon.qbusiness.plugin.Tag(serviceTag.key(), serviceTag.value()))
        .toList();
  }
}
