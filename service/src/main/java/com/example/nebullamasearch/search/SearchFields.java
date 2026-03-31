package com.example.nebullamasearch.search;

import java.util.List;

public final class SearchFields {

  private SearchFields() {}

  public static final List<String> MULTI_MATCH_FIELDS =
      List.of(
          "name",
          "description",
          "notes",
          "biography",
          "abstract",
          "title",
          "target_name",
          "known_for");

  public static final List<String> YEAR_FIELDS =
      List.of("year", "launch_year", "discovery_year");

  public static final String EMBEDDING_FIELD = "embedding";
}
