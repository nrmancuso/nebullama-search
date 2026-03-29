package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Mission {
  public String id;
  public String name;
  public String agency;

  @JsonProperty("mission_type")
  public String missionType;

  @JsonProperty("launch_year")
  public Integer launchYear;

  public String status;
  public List<String> targets;
  public String description;

  @JsonProperty("resource_type")
  public ResourceType resourceType;

  public float[] embedding;
}
