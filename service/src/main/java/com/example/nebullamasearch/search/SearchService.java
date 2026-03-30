package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

  private final OpenSearchClient openSearchClient;
  private final OllamaEmbeddingService embeddingService;

  @Value("${search.knn-k:10}")
  private int knnK;

  @Value("${search.hybrid-weight.bm25:0.4}")
  private float bm25Weight;

  @Value("${search.hybrid-weight.knn:0.6}")
  private float knnWeight;

  public SearchService(OpenSearchClient openSearchClient, OllamaEmbeddingService embeddingService) {
    this.openSearchClient = openSearchClient;
    this.embeddingService = embeddingService;
  }

  // -------------------------------------------------------------------------
  // BM25
  // -------------------------------------------------------------------------

  public com.example.nebullamasearch.search.SearchResponse searchBM25(SearchRequest request) {
    final String indexNames = resolveIndexNames(request);
    final Pagination pagination =
        request.pagination() != null ? request.pagination() : Pagination.defaultPagination();
    final List<Query> filterClauses = buildFilterClauses(request.filters());

    // multi_match query
    final Query multiMatch =
        Query.of(
            q ->
                q.multiMatch(
                    mm ->
                        mm.query(request.query())
                            .fields(
                                List.of(
                                    "name",
                                    "description",
                                    "notes",
                                    "biography",
                                    "abstract",
                                    "title",
                                    "target_name",
                                    "known_for"))));

    // bool: must=[multiMatch], filter=[...]
    final Query boolQuery =
        Query.of(
            q ->
                q.bool(
                    b -> {
                      b.must(multiMatch);
                      if (!filterClauses.isEmpty()) {
                        b.filter(filterClauses);
                      }
                      return b;
                    }));

    try {
      final SearchResponse<Map> response =
          openSearchClient.search(
              s ->
                  s.index(indexNames)
                      .from(pagination.from())
                      .size(pagination.size())
                      .query(boolQuery),
              Map.class);
      return mapResponse(response);
    } catch (IOException e) {
      throw new RuntimeException("BM25 search failed", e);
    }
  }

  // -------------------------------------------------------------------------
  // k-NN
  // -------------------------------------------------------------------------

  public com.example.nebullamasearch.search.SearchResponse searchKNN(SearchRequest request) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  // -------------------------------------------------------------------------
  // Hybrid
  // -------------------------------------------------------------------------

  public com.example.nebullamasearch.search.SearchResponse searchHybrid(SearchRequest request) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private String resolveIndexNames(SearchRequest request) {
    final List<ResourceType> types =
        (request.resourceTypes() != null && !request.resourceTypes().isEmpty())
            ? request.resourceTypes()
            : Arrays.asList(ResourceType.values());
    return types.stream()
        .map(ResourceType::indexName)
        .collect(java.util.stream.Collectors.joining(","));
  }

  private List<Query> buildFilterClauses(SearchFilters filters) {
    if (filters == null) {
      return Collections.emptyList();
    }
    final List<Query> clauses = new ArrayList<>();

    if (filters.objectType() != null) {
      clauses.add(termQuery("object_type", filters.objectType()));
    }
    if (filters.agency() != null) {
      clauses.add(termQuery("agency", filters.agency()));
    }
    if (filters.status() != null) {
      clauses.add(termQuery("status", filters.status()));
    }
    if (filters.wavelengthBand() != null) {
      clauses.add(termQuery("wavelength_band", filters.wavelengthBand()));
    }
    if (filters.journal() != null) {
      clauses.add(termQuery("journal", filters.journal()));
    }
    if (filters.nationality() != null) {
      clauses.add(termQuery("nationality", filters.nationality()));
    }
    if (filters.yearFrom() != null || filters.yearTo() != null) {
      // Year is stored under different field names per index.
      // Use a bool.should wrapping all three year fields so a doc matches
      // if ANY of its year fields falls in the range.
      final List<Query> yearShoulds = new ArrayList<>();
      yearShoulds.add(rangeQuery("year", filters.yearFrom(), filters.yearTo()));
      yearShoulds.add(rangeQuery("launch_year", filters.yearFrom(), filters.yearTo()));
      yearShoulds.add(rangeQuery("discovery_year", filters.yearFrom(), filters.yearTo()));
      clauses.add(Query.of(q -> q.bool(b -> b.should(yearShoulds).minimumShouldMatch("1"))));
    }
    return clauses;
  }

  private Query termQuery(String field, String value) {
    return Query.of(q -> q.term(t -> t.field(field + ".keyword").value(FieldValue.of(value))));
  }

  private Query rangeQuery(String field, Integer from, Integer to) {
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

  @SuppressWarnings("unchecked")
  private com.example.nebullamasearch.search.SearchResponse mapResponse(
      SearchResponse<Map> response) {
    final long total = response.hits().total() != null ? response.hits().total().value() : 0L;
    final List<SearchHit> hits =
        response.hits().hits().stream()
            .map(
                hit ->
                    new SearchHit(
                        hit.id(),
                        ResourceType.fromIndexName(hit.index()),
                        hit.score() != null ? hit.score().floatValue() : 0f,
                        hit.source() != null ? hit.source() : Map.of()))
            .collect(java.util.stream.Collectors.toList());
    return new com.example.nebullamasearch.search.SearchResponse(total, hits);
  }
}
