package software.amazon.qbusiness.webexperience;

import java.util.List;

import software.amazon.awssdk.services.qbusiness.model.Tag;

public class TagHelper {

  /**
   * Get model tags from service tags.
   *
   * @param serviceTags List of service tags
   * @return List of model tags
   */
  public static List<software.amazon.qbusiness.webexperience.Tag> modelTagsFromServiceTags(final List<Tag> serviceTags) {
    return serviceTags.stream()
        .map(serviceTag -> new software.amazon.qbusiness.webexperience.Tag(serviceTag.key(), serviceTag.value())
        )
        .toList();
  }
}
