# Issue #38: Query Builder Abstraction

## What this is about

Query construction is tangled into `SearchService` with duplicated field lists, per-field
copy-paste filter building, and no way to unit test query construction independently. This
design extracts query building into a dedicated `SearchQueryBuilder` backed by a data-driven
`FilterBuilder` and a `SearchFields` constants class.

## Key terms

| Term | Meaning |
| ---- | ------- |
| BM25 | Keyword search using OpenSearch multi_match |
| k-NN | Semantic vector search using OpenSearch knn query |
| Hybrid | BM25 + k-NN combined via OpenSearch hybrid query and normalization pipeline |
| dis_max | OpenSearch query that matches across multiple fields and takes the best score |
| Efficient filter | Filters passed inside the kNN clause so candidates are constrained before ANN search |

## Approach

Single `SearchQueryBuilder` that composes from well-factored private methods. The three
modes share too much logic for separate strategy classes; hybrid is literally BM25 + kNN
composed. A separate `FilterBuilder` handles data-driven filter clause construction. A
`SearchFields` constants class holds field lists.

## Components

### SearchFields

Constants class in `com.example.nebullamasearch.search`. Final class, private constructor.

Fields:

- `MULTI_MATCH_FIELDS` -- `List<String>` of the 8 text fields: `name`, `description`,
  `notes`, `biography`, `abstract`, `title`, `target_name`, `known_for`
- `YEAR_FIELDS` -- `List<String>` of the 3 year field variants: `year`, `launch_year`,
  `discovery_year`
- `EMBEDDING_FIELD` -- `String` constant: `"embedding"`

### FilterBuilder

Separate class in `com.example.nebullamasearch.search`. No Spring dependencies; plain
instantiation.

Public API:

- `List<Query> buildFilterClauses(SearchFilters filters)`

Implementation:

- A private static list of `FilterField` records (private to `FilterBuilder`), each holding:
  - OpenSearch field name (e.g. `"agency"`)
  - Accessor function `Function<SearchFilters, String>` (e.g. `SearchFilters::agency`)
- One loop iterates the list, null-checks the accessor result, and builds term queries for
  non-null values. Adding a new filter field means adding one entry to the list.
- Year range handled after the loop: if `yearFrom` or `yearTo` is non-null, builds a
  dis_max query across `SearchFields.YEAR_FIELDS` with range queries. Uses dis_max because
  it works inside kNN filters where `bool.should` with `minimumShouldMatch` does not.

### SearchQueryBuilder

Spring bean in `com.example.nebullamasearch.search`. Constructor dependencies:

- `OllamaEmbeddingService` (for vector generation on kNN and hybrid paths)
- `FilterBuilder`
- `knnK` from `@Value("${search.knn-k:10}")`

Public API:

- `Query buildQuery(SearchMode mode, SearchRequest request)` -- dispatches via switch
  expression to the appropriate private method

Private methods:

- `buildBM25Query(SearchRequest)` -- multi_match over `SearchFields.MULTI_MATCH_FIELDS`
  wrapped in bool with filters. Falls back to matchAll when no query text.
- `buildKNNQuery(SearchRequest)` -- calls `embeddingService.embed()`, builds kNN query on
  `SearchFields.EMBEDDING_FIELD` with efficient filter.
- `buildHybridQuery(SearchRequest)` -- composes BM25 sub-query + kNN sub-query into a
  hybrid query. Reuses shared helpers.
- `buildMultiMatchQuery(String query)` -- shared helper for multi_match construction used
  by both BM25 and hybrid.
- `buildKNNClause(float[] vector, List<Query> filters)` -- shared helper for kNN clause
  used by both kNN and hybrid.

### SearchService changes

Becomes a thin execution layer. The three public search methods collapse into one:

```java
public SearchResponse search(SearchMode mode, SearchRequest request)
```

This method:

1. Calls `queryBuilder.buildQuery(mode, request)` to get the `Query`
2. Resolves index names (stays in `SearchService`; execution concern)
3. For hybrid mode, sets `TransportOptions` with `search_pipeline=hybrid-pipeline`
   (transport concern, not query construction)
4. Executes the search against OpenSearch
5. Maps the response

`SearchController` calls `searchService.search(mode, request)` instead of picking between
three methods.

### ObjectMapper consolidation

Spring Boot auto-configures an `ObjectMapper` singleton. Most production code already
injects it via constructor. One fix:

- `IndexInitializer`: replace `private final ObjectMapper objectMapper = new ObjectMapper()`
  with constructor injection of the Spring-managed instance.

Test code: use a shared `static final ObjectMapper` per test class instead of creating new
instances inline.

## Testing

Pure unit tests for `SearchQueryBuilder`. No containers, no Spring context.

Setup:

- `FilterBuilder` instantiated directly
- `OllamaEmbeddingService` mocked (returns a fixed vector for any input)
- `SearchQueryBuilder` instantiated with the mock + `FilterBuilder` + a fixed `knnK` value

Test shape:

- Each test defines a GraphQL query string, parses it into the equivalent `SearchRequest`
- Calls `queryBuilder.buildQuery(mode, request)`
- Serializes the resulting `Query` to JSON using the OpenSearch client's `JsonpMapper`
- Asserts on the JSON structure

Test cases:

- BM25 with query only
- BM25 with query + filters
- BM25 with no query (filters only, produces matchAll with filter)
- kNN with query only
- kNN with query + filters (efficient filter inside kNN clause)
- Hybrid with query + filters
- Year range filter produces dis_max across `SearchFields.YEAR_FIELDS`
- Each term filter maps to the correct OpenSearch field name
- Multi-match fields match `SearchFields.MULTI_MATCH_FIELDS` exactly

## What does not change

- GraphQL schema: no changes to the API contract
- `SearchController` mode selection logic: stays the same (query + filters = HYBRID,
  query only = SEMANTIC, otherwise = KEYWORD)
- Integration tests: existing Testcontainer-based tests continue to validate end-to-end
  behavior
- `OllamaEmbeddingService`, `OllamaChatService`, `IntentExtractionService`: untouched
  beyond ObjectMapper consolidation
- OpenSearch index mappings: no changes
- Hybrid pipeline configuration in `IndexInitializer`: no changes
