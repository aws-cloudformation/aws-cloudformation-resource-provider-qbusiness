package software.amazon.qbusiness.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;

import software.amazon.awssdk.services.qbusiness.QBusinessClient;
import software.amazon.awssdk.services.qbusiness.model.Tag;
import software.amazon.awssdk.services.qbusiness.model.TagResourceRequest;
import software.amazon.awssdk.services.qbusiness.model.UntagResourceRequest;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.StdCallbackContext;

/**
 * Class containing common handler tag operations.
 */
public final class TagUtils {
  private static final String PROP_NAME_TAGS = "Tags";
  private static final String PROP_NAME_KEY = "Key";
  private static final String PROP_NAME_VALUE = "Value";

  private TagUtils() {
  }

  public static <T> List<Tag> mergeCreateHandlerTagsToSdkTags(
      final ResourceHandlerRequest<T> handlerRequest,
      final T model
  ) {
    Map<String, String> modelTags = getModelJsonTags(model);
    return mergeCreateHandlerTagsToSdkTags(modelTags, handlerRequest);
  }

  public static <RType, CtxType extends StdCallbackContext> ProgressEvent<RType, CtxType> updateTags(
      final String typeName,
      final ProgressEvent<RType, CtxType> progressEvent,
      final ResourceHandlerRequest<RType> handlerRequest,
      final String resourceArn,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger
  ) {
    logger.log("Checking if there are updates to make to tags for resource: %s".formatted(resourceArn));
    Map<String, String> previousTags = getPreviouslyAttachedTags(handlerRequest);
    Map<String, String> desiredTags = getNewDesiredTags(handlerRequest);
    if (!shouldUpdateTags(previousTags, desiredTags)) {
      logger.log("No tag updates to be made for: %s".formatted(resourceArn));
      return progressEvent;
    }

    try {
      Map<String, String> tagsToAdd = generateTagsToAdd(previousTags, desiredTags);
      if (MapUtils.isNotEmpty(tagsToAdd)) {
        invokeTagResource(resourceArn, tagsToAdd, proxyClient, logger);
      }

      Set<String> tagsToRemove = generateTagsToRemove(previousTags, desiredTags);
      if (CollectionUtils.isNotEmpty(tagsToRemove)) {
        invokeUntagResource(resourceArn, tagsToRemove, proxyClient, logger);
      }
    } catch (Exception e) {
      return ErrorUtils.handleError(
          progressEvent.getResourceModel(), resourceArn, e,
          progressEvent.getCallbackContext(), logger, typeName, "Tag/Untag"
      );
    }

    return progressEvent;
  }

  private static void invokeTagResource(
      final String resourceArn,
      final Map<String, String> tagsToAdd,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger
  ) {
    logger.log("Invoking tag resource with %s tags to add".formatted(tagsToAdd.size()));

    List<Tag> toTags = tagsToAdd.entrySet()
        .stream()
        .map(entry -> Tag.builder()
            .key(entry.getKey())
            .value(entry.getValue())
            .build())
        .toList();

    var request = TagResourceRequest.builder()
        .tags(toTags)
        .resourceARN(resourceArn)
        .build();
    proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::tagResource);
    logger.log("Finished invoking tag resource.");
  }

  private static void invokeUntagResource(
      final String resourceArn,
      final Set<String> tagsToRemove,
      final ProxyClient<QBusinessClient> proxyClient,
      final Logger logger
  ) {
    logger.log("Invoking untag resource with %s tags to remove".formatted(tagsToRemove.size()));
    var request = UntagResourceRequest.builder()
        .tagKeys(tagsToRemove)
        .resourceARN(resourceArn)
        .build();
    proxyClient.injectCredentialsAndInvokeV2(request, proxyClient.client()::untagResource);
    logger.log("Finished invoking untag resource");
  }

  private static <T> List<Tag> mergeCreateHandlerTagsToSdkTags(
      final Map<String, String> modelTags,
      final ResourceHandlerRequest<T> handlerRequest
  ) {
    var merged = Stream.of(handlerRequest.getDesiredResourceTags(), modelTags, handlerRequest.getSystemTags())
        .filter(Objects::nonNull)
        .flatMap(map -> map.entrySet().stream())
        .map(entry -> Tag.builder()
            .key(entry.getKey())
            .value(entry.getValue())
            .build()
        )
        .collect(Collectors.toList());

    if (merged.isEmpty()) {
      return null;
    }
    return  merged;
  }

  private static boolean shouldUpdateTags(
      final Map<String, String> allPreviousTags,
      final Map<String, String> allDesiredTags
  ) {
    return ObjectUtils.notEqual(allPreviousTags, allDesiredTags);
  }

  private static <T> Map<String, String> getPreviouslyAttachedTags(
      final ResourceHandlerRequest<T> handlerRequest
  ) {
    var previousState = handlerRequest.getPreviousResourceState();
    Map<String, String> modelTags = getModelJsonTags(previousState);
    var systemTags = handlerRequest.getPreviousSystemTags();
    var desiredResourceTags = handlerRequest.getPreviousResourceTags();
    return mergedTags(modelTags, systemTags, desiredResourceTags);
  }

  private static <T> Map<String, String> getNewDesiredTags(
      final ResourceHandlerRequest<T> handlerRequest
  ) {
    var model = handlerRequest.getDesiredResourceState();
    Map<String, String> modelTags = getModelJsonTags(model);
    var systemTags = handlerRequest.getSystemTags();
    var desiredResourceTags = handlerRequest.getDesiredResourceTags();
    return mergedTags(modelTags, systemTags, desiredResourceTags);
  }

  private static Map<String, String> generateTagsToAdd(
      final Map<String, String> previousTags,
      final Map<String, String> desiredTags
  ) {
    return desiredTags.entrySet().stream()
        .filter(e -> !previousTags.containsKey(e.getKey()) || !Objects.equals(previousTags.get(e.getKey()), e.getValue()))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue));
  }

  private static Set<String> generateTagsToRemove(
      final Map<String, String> previousTags,
      final Map<String, String> desiredTags
  ) {
    final Set<String> desiredTagNames = desiredTags.keySet();

    return previousTags.keySet().stream()
        .filter(tagName -> !desiredTagNames.contains(tagName))
        .collect(Collectors.toSet());
  }

  private static Map<String, String> getModelJsonTags(Object model) {
    var mapper = new ObjectMapper();
    JsonNode modelAsJson = mapper.valueToTree(model);
    JsonNode tags = modelAsJson.get(PROP_NAME_TAGS);

    if (tags == null || tags.isNull()) {
      return null;
    }

    if (!tags.isArray()) {
      throw new CfnGeneralServiceException("Error processing tags as a list");
    }

    return StreamSupport.stream(tags.spliterator(), false)
        .collect(Collectors.toMap(
            tag -> tag.get(PROP_NAME_KEY).asText(),
            tag -> tag.get(PROP_NAME_VALUE).asText()
        ));
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
