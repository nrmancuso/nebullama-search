package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.Map;

public record SearchHit(
    String id, ResourceType resourceType, float score, Map<String, Object> source) {}
