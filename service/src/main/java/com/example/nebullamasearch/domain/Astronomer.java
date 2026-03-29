package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Astronomer {
    public String id;
    public String name;
    @JsonProperty("birth_year")           public Integer birthYear;
    @JsonProperty("death_year")           public Integer deathYear;
    public String nationality;
    @JsonProperty("known_for")            public String knownFor;
    @JsonProperty("associated_objects")   public List<String> associatedObjects;
    @JsonProperty("associated_missions")  public List<String> associatedMissions;
    public String biography;
    @JsonProperty("resource_type")        public ResourceType resourceType;
    public float[] embedding;
}
