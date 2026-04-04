package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.TransportOptions;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

  private final OpenSearchClient openSearchClient;
  private final SearchQueryBuilder queryBuilder;

  public SearchService(OpenSearchClient openSearchClient, SearchQueryBuilder queryBuilder) {
    this.openSearchClient = openSearchClient;
    this.queryBuilder = queryBuilder;
  }

  public SearchResponse search(SearchMode mode, SearchRequest request) {
    final org.opensearch.client.opensearch._types.query_dsl.Query query =
        queryBuilder.buildQuery(mode, request);
    final String indexNames = resolveIndexNames(request);
    final Pagination pagination =
        request.pagination() != null ? request.pagination() : Pagination.defaultPagination();

    try {
      final org.opensearch.client.opensearch.core.SearchResponse<Map> response;
      if (mode == SearchMode.HYBRID) {
        final TransportOptions existingOptions = openSearchClient._transportOptions();
        final TransportOptions hybridOptions =
            (existingOptions != null ? existingOptions.toBuilder() : TransportOptions.builder())
                .setParameter("search_pipeline", "hybrid-pipeline")
                .build();
        response =
            openSearchClient
                .withTransportOptions(hybridOptions)
                .search(
                    s ->
                        s.index(indexNames)
                            .from(pagination.from())
                            .size(pagination.size())
                            .query(query),
                    Map.class);
      } else {
        response =
            openSearchClient.search(
                s ->
                    s.index(indexNames)
                        .from(pagination.from())
                        .size(pagination.size())
                        .query(query),
                Map.class);
      }
      return mapResponse(response);
    } catch (IOException e) {
      throw new RuntimeException(mode + " search failed", e);
    }
  }

  private String resolveIndexNames(SearchRequest request) {
    final List<ResourceType> types =
        (request.resourceTypes() != null && !request.resourceTypes().isEmpty())
            ? request.resourceTypes()
            : Arrays.asList(ResourceType.values());
    return types.stream().map(ResourceType::indexName).collect(Collectors.joining(","));
  }

  @SuppressWarnings("unchecked")
  private SearchResponse mapResponse(
      org.opensearch.client.opensearch.core.SearchResponse<Map> response) {
    final long total = response.hits().total() != null ? response.hits().total().value() : 0L;
    final List<SearchHit> hits =
        response.hits().hits().stream()
            .map(
                hit ->
                    new SearchHit(
                        hit.id(),
                        ResourceType.fromIndexName(hit.index()),
                        hit.score() != null ? hit.score().floatValue() : 0f,
                        sanitizeSource(hit.source())))
            .collect(Collectors.toList());
    return new SearchResponse(total, hits);
  }

  private Map<String, Object> sanitizeSource(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    final Map<String, Object> sanitized = new LinkedHashMap<>(source);
    sanitized.remove(SearchFields.EMBEDDING_FIELD);
    return Collections.unmodifiableMap(sanitized);
  }
}
