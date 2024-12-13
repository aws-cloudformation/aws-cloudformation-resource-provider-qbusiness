package software.amazon.qbusiness.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

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
      final ResourceHandlerRequest<T> handlerRequest
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

  public static boolean shouldUpdateTags(
      final Map<String, String> allPreviousTags,
      final Map<String, String> allDesiredTags
  ) {
    return ObjectUtils.notEqual(allPreviousTags, allDesiredTags);
  }

  public static <T> Map<String, String> getPreviouslyAttachedTags(
      final Map<String, String> previousModelTags,
      final ResourceHandlerRequest<T> handlerRequest
  ) {
    return mergedTags(
        previousModelTags,
        handlerRequest.getPreviousSystemTags(),
        handlerRequest.getPreviousResourceTags()
    );
  }

  public static <T> Map<String, String> getNewDesiredTags(
      final Map<String, String> modelTags,
      final ResourceHandlerRequest<T> handlerRequest
  ) {
    return mergedTags(
        modelTags,
        handlerRequest.getSystemTags(),
        handlerRequest.getDesiredResourceTags()
    );
  }

  public static Map<String, String> generateTagsToAdd(
      final Map<String, String> previousTags,
      final Map<String, String> desiredTags
  ) {
    return desiredTags.entrySet().stream()
        .filter(e -> !previousTags.containsKey(e.getKey()) || !Objects.equals(previousTags.get(e.getKey()), e.getValue()))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue));
  }

  public static Set<String> generateTagsToRemove(
      final Map<String, String> previousTags,
      final Map<String, String> desiredTags
  ) {
    final Set<String> desiredTagNames = desiredTags.keySet();

    return previousTags.keySet().stream()
        .filter(tagName -> !desiredTagNames.contains(tagName))
        .collect(Collectors.toSet());
  }

  private static Map<String, String> mergedTags(
      Map<String, String> modelTags,
      Map<String, String> systemTags,
      Map<String, String> resourceTags
  ) {
    var combined = new HashMap<String, String>();
    Stream.of(
        Optional.ofNullable(modelTags),
        Optional.ofNullable(systemTags),
        Optional.ofNullable(resourceTags)
    ).flatMap(Optional::stream)
        .forEach(combined::putAll);
    return combined;
  }

}
