package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CelestialObject {
  public String id;
  public String name;
  public List<String> designations;

  @JsonProperty("object_type")
  public String objectType;

  public String constellation;

  @JsonProperty("distance_ly")
  public Double distanceLy;

  public String description;

  @JsonProperty("discovered_by")
  public String discoveredBy;

  @JsonProperty("discovery_year")
  public Integer discoveryYear;

  @JsonProperty("resource_type")
  public ResourceType resourceType;

  public float[] embedding;
}
