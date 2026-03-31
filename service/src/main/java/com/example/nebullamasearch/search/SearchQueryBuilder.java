package com.example.nebullamasearch.search;

import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SearchQueryBuilder {

  private final OllamaEmbeddingService embeddingService;
  private final FilterBuilder filterBuilder;
  private final int knnK;

  public SearchQueryBuilder(
      OllamaEmbeddingService embeddingService,
      FilterBuilder filterBuilder,
      @Value("${search.knn-k:10}") int knnK) {
    this.embeddingService = embeddingService;
    this.filterBuilder = filterBuilder;
    this.knnK = knnK;
  }

  public Query buildQuery(SearchMode mode, SearchRequest request) {
    return switch (mode) {
      case KEYWORD -> buildBM25Query(request);
      case SEMANTIC -> buildKNNQuery(request);
      case HYBRID -> buildHybridQuery(request);
    };
  }

  private Query buildBM25Query(SearchRequest request) {
    final List<Query> filterClauses = filterBuilder.buildFilterClauses(request.filters());
    final boolean hasQuery = request.query() != null && !request.query().isBlank();

    if (hasQuery) {
      final Query multiMatch = buildMultiMatchQuery(request.query());
      return Query.of(
          q ->
              q.bool(
                  b -> {
                    b.must(multiMatch);
                    if (!filterClauses.isEmpty()) {
                      b.filter(filterClauses);
                    }
                    return b;
                  }));
    }

    return filterClauses.isEmpty()
        ? Query.of(q -> q.matchAll(m -> m))
        : Query.of(q -> q.bool(b -> b.filter(filterClauses)));
  }

  private Query buildKNNQuery(SearchRequest request) {
    final float[] queryVector = embeddingService.embed(request.query());
    final List<Query> filterClauses = filterBuilder.buildFilterClauses(request.filters());
    return buildKNNClause(queryVector, filterClauses);
  }

  private Query buildHybridQuery(SearchRequest request) {
    final float[] queryVector = embeddingService.embed(request.query());
    final List<Query> filterClauses = filterBuilder.buildFilterClauses(request.filters());

    final Query multiMatch = buildMultiMatchQuery(request.query());
    final Query bm25SubQuery =
        filterClauses.isEmpty()
            ? multiMatch
            : Query.of(q -> q.bool(b -> b.must(multiMatch).filter(filterClauses)));

    final Query knnSubQuery = buildKNNClause(queryVector, filterClauses);

    return Query.of(q -> q.hybrid(h -> h.queries(bm25SubQuery, knnSubQuery)));
  }

  private Query buildMultiMatchQuery(String query) {
    return Query.of(
        q -> q.multiMatch(mm -> mm.query(query).fields(SearchFields.MULTI_MATCH_FIELDS)));
  }

  private Query buildKNNClause(float[] vector, List<Query> filterClauses) {
    final int k = knnK;
    if (filterClauses.isEmpty()) {
      return Query.of(
          q -> q.knn(knn -> knn.field(SearchFields.EMBEDDING_FIELD).vector(vector).k(k)));
    }
    return Query.of(
        q ->
            q.knn(
                knn ->
                    knn.field(SearchFields.EMBEDDING_FIELD)
                        .vector(vector)
                        .k(k)
                        .filter(Query.of(f -> f.bool(b -> b.filter(filterClauses))))));
  }
}
