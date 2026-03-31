package com.example.nebullamasearch.search.dto;

import java.util.List;

public record SearchResultsDto(
    int total, List<SearchHitDto> hits, QueryInterpretationResultDto interpretation) {}
