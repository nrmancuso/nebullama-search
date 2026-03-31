package com.example.nebullamasearch.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

@Component
public class FilterBuilder {

  private record FilterField(String fieldName, Function<SearchFilters, String> accessor) {}

  private static final List<FilterField> TERM_FILTERS =
      List.of(
          new FilterField("object_type", SearchFilters::objectType),
          new FilterField("agency", SearchFilters::agency),
          new FilterField("status", SearchFilters::status),
          new FilterField("wavelength_band", SearchFilters::wavelengthBand),
          new FilterField("journal", SearchFilters::journal),
          new FilterField("nationality", SearchFilters::nationality));

  public List<Query> buildFilterClauses(SearchFilters filters) {
    if (filters == null) {
      return Collections.emptyList();
    }

    final List<Query> clauses = new ArrayList<>();
    for (final FilterField field : TERM_FILTERS) {
      final String value = field.accessor().apply(filters);
      if (value != null) {
        clauses.add(
            Query.of(
                q -> q.term(t -> t.field(field.fieldName()).value(FieldValue.of(value)))));
      }
    }

    if (filters.yearFrom() != null || filters.yearTo() != null) {
      final List<Query> yearQueries = new ArrayList<>();
      for (final String yearField : SearchFields.YEAR_FIELDS) {
        yearQueries.add(buildRangeQuery(yearField, filters.yearFrom(), filters.yearTo()));
      }
      clauses.add(Query.of(q -> q.disMax(d -> d.queries(yearQueries))));
    }

    return clauses;
  }

  private Query buildRangeQuery(String field, Integer from, Integer to) {
    return Query.of(
        q ->
            q.range(
                r -> {
                  r.field(field);
                  if (from != null) {
                    r.gte(JsonData.of(from));
                  }
                  if (to != null) {
                    r.lte(JsonData.of(to));
                  }
                  return r;
                }));
  }
}
