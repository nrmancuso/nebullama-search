package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceType {
  CELESTIAL_OBJECTS("celestial_objects"),
  MISSIONS("missions"),
  OBSERVATIONS("observations"),
  ASTRONOMERS("astronomers"),
  PUBLICATIONS("publications");

  private final String indexName;

  ResourceType(String indexName) {
    this.indexName = indexName;
  }

  public String indexName() {
    return indexName;
  }

  @JsonValue
  public String toValue() {
    return name();
  }

  @JsonCreator
  public static ResourceType fromValue(String value) {
    for (ResourceType type : values()) {
      if (type.name().equalsIgnoreCase(value) || type.indexName.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown ResourceType: " + value);
  }

  public static ResourceType fromIndexName(String indexName) {
    for (ResourceType type : values()) {
      if (type.indexName.equals(indexName)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No ResourceType for index: " + indexName);
  }
}
