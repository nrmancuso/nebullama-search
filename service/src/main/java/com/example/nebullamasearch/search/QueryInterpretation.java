package com.example.nebullamasearch.search;

import java.util.Map;

public record QueryInterpretation(
    String rewrittenQuery, Map<String, Object> extractedFilters, SearchMode searchMode) {

  public static QueryInterpretation fallback(String rawQuery) {
    return new QueryInterpretation(rawQuery, Map.of(), SearchMode.HYBRID);
  }
}
