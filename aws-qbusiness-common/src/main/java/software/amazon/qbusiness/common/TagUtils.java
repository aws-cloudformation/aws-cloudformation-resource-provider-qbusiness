package software.amazon.qbusiness.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

/**
 * Class containing common handler tag operations.
 */
public final class TagUtils {

  private TagUtils() {
  }

  public static <T> List<Tag> mergeCreateHandlerTagsToSdkTags(
      final Map<String, String> modelTags,
      ResourceHandlerRequest<T> handlerRequest
  ) {
    return Stream.of(handlerRequest.getDesiredResourceTags(), modelTags, handlerRequest.getSystemTags())
        .filter(Objects::nonNull)
        .flatMap(map -> map.entrySet().stream())
        .map(entry -> Tag.builder()
            .key(entry.getKey())
            .value(entry.getValue())
            .build()
        )
        .collect(Collectors.toList());
  }
}
