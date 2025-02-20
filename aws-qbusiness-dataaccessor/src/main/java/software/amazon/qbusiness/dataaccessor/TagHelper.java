package software.amazon.qbusiness.dataaccessor;

import java.util.List;

public class TagHelper {

    public static List<software.amazon.qbusiness.dataaccessor.Tag> cfnTagsFromServiceTags(
        List<software.amazon.awssdk.services.qbusiness.model.Tag> serviceTags
    ) {
        return serviceTags.stream()
            .map(serviceTag -> new software.amazon.qbusiness.dataaccessor.Tag(serviceTag.key(), serviceTag.value()))
            .toList();
    }

}
