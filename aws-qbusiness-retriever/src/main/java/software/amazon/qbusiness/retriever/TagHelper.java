package software.amazon.qbusiness.retriever;

import java.util.List;

public class TagHelper {

  public static List<software.amazon.qbusiness.retriever.Tag> cfnTagsFromServiceTags(
      List<software.amazon.awssdk.services.qbusiness.model.Tag> serviceTags
  ) {
    return serviceTags.stream()
        .map(serviceTag -> new software.amazon.qbusiness.retriever.Tag(serviceTag.key(), serviceTag.value()))
        .toList();
  }
}
