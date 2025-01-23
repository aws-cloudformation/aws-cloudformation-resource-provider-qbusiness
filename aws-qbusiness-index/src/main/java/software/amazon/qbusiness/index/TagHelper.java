package software.amazon.qbusiness.index;

import java.util.List;

import software.amazon.awssdk.services.qbusiness.model.Tag;

public class TagHelper {

  /**
   * Get model tags from service tags.
   *
   * @param serviceTags List of service tags
   * @return List of model tags
   */
  public static List<software.amazon.qbusiness.index.Tag> modelTagsFromServiceTags(
      List<Tag> serviceTags
  ) {
    return serviceTags.stream()
        .map(serviceTag -> new software.amazon.qbusiness.index.Tag(serviceTag.key(), serviceTag.value())
        )
        .toList();
  }

}
