package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Publication {
    public String id;
    public String title;
    public List<String> authors;
    public Integer year;
    public String journal;
    @JsonProperty("abstract")      public String abstractText;
    public List<String> topics;
    public String doi;
    @JsonProperty("resource_type") public ResourceType resourceType;
    public float[] embedding;
}
