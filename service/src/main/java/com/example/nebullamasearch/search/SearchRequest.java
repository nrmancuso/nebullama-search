package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;

public record SearchRequest(
    String query, List<ResourceType> resourceTypes, SearchFilters filters, Pagination pagination) {}
