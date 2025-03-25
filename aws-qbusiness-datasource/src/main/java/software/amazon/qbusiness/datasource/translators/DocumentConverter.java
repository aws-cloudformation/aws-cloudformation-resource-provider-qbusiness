package software.amazon.qbusiness.datasource.translators;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.utils.ImmutableMap;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

public final class DocumentConverter {

  private DocumentConverter() {
  }

  public static Map<String, Object> convertDocumentToMap(Document document) {
    if (Objects.isNull(document)) {
      return null;
    }
    if (!document.isMap()) {
      throw new CfnGeneralServiceException("Upstream service returned an unexpected template document.");
    }

    ImmutableMap.Builder<String, Object> outputMapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Document> documentEntry : document.asMap().entrySet()) {
      var key = documentEntry.getKey();
      var value = documentEntry.getValue().unwrap();
      outputMapBuilder.put(key, value);
    }

    return outputMapBuilder.build();
  }

  public static Document convertToMapToDocument(Map<String, Object> objectMap) {
    if (objectMap == null) {
      return null;
    }

    ImmutableMap.Builder<String, Document> mapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Object> mapEntry : objectMap.entrySet()) {
      var key = mapEntry.getKey();
      var value = mapEntry.getValue();

      mapBuilder.put(key, objectToDocument(value));
    }

    return Document.fromMap(mapBuilder.build());
  }

  @SuppressWarnings("rawtypes")
  private static Document objectToDocument(Object value) {
    if (value instanceof Boolean bool) {
      return Document.fromBoolean(bool);
    } else if (value instanceof String string) {
      // Due to how yaml handles values, we'll receive boolean values as strings.
      // Parse "true"/"false" as booleans to allow creating datasources like webcrawler: https://docs.aws.amazon.com/amazonq/latest/qbusiness-ug/web-crawler-api.html
      if ("true".equals(string) || "false".equals(string)) {
        var boolValue = Boolean.parseBoolean(string);
        return Document.fromBoolean(boolValue);
      }
      return Document.fromString(string);
    } else if (value instanceof Number) {
      if (value instanceof Integer integer) {
        return Document.fromNumber(integer);
      } else if (value instanceof Long longVal) {
        return Document.fromNumber(longVal);
      } else if (value instanceof Double doubleVal) {
        return Document.fromNumber(doubleVal);
      } else {
        throw new CfnInvalidRequestException("Unexpected number type found: %s. Expecting Integer, Long, or Double values only.".formatted(value));
      }
    } else if (value instanceof List list) {
      ImmutableList.Builder<Document> converted = ImmutableList.builder();
      for (Object item : list) {
        converted.add(objectToDocument(item));
      }
      return Document.fromList(converted.build());
    } else if (value instanceof Map) {
      @SuppressWarnings("unchecked")
      var rawMap = (Map<String, Object>) value;
      return convertToMapToDocument(rawMap);
    } else {
      throw new CfnInvalidRequestException("Unexpected document value found: %s".formatted(value));
    }
  }

}
