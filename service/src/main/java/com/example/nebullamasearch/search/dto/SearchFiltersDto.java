package com.example.nebullamasearch.search.dto;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;

public record SearchFiltersDto(
    List<ResourceType> resourceTypes,
    String objectType,
    String agency,
    String status,
    String wavelengthBand,
    String journal,
    String nationality,
    Integer yearFrom,
    Integer yearTo) {}
