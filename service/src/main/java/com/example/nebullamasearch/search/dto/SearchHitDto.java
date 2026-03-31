package com.example.nebullamasearch.search.dto;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.Map;

public record SearchHitDto(
    String id, ResourceType resourceType, float score, Map<String, Object> source) {}
