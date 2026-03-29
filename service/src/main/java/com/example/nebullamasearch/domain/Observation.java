package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Observation {
  public String id;

  @JsonProperty("target_name")
  public String targetName;

  public String instrument;
  public String observatory;

  @JsonProperty("observation_date")
  public String observationDate;

  @JsonProperty("wavelength_band")
  public String wavelengthBand;

  public String notes;

  @JsonProperty("resource_type")
  public ResourceType resourceType;

  public float[] embedding;
}
