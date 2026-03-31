package com.example.nebullamasearch.search.dto;

import com.example.nebullamasearch.search.SearchMode;
import java.util.Map;

public record QueryInterpretationResultDto(
    String rewrittenQuery, Map<String, Object> extractedFilters, SearchMode searchMode) {}
