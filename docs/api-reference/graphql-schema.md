# GraphQL Schema Reference

**Endpoint:** `POST /graphql`
**GraphiQL IDE:** `http://localhost:8080/graphiql`

## Schema

```graphql
scalar JSON

type Query {
  search(input: SearchInput!): SearchResults!
  searchIndex(resourceType: ResourceType!, input: SearchInput!): SearchResults!
}

input SearchInput {
  query: String!
  filters: SearchFilters
  pagination: Pagination
}

input SearchFilters {
  resourceTypes: [ResourceType!]   # limit to specific indexes
  objectType: String               # celestial_objects: star, nebula, galaxy, pulsar, ...
  agency: String                   # missions: NASA, ESA, JAXA, ...
  status: String                   # missions: active, retired, planned, lost
  wavelengthBand: String           # observations: optical, infrared, radio, x-ray, gamma, uv
  journal: String                  # publications: ApJ, MNRAS, Nature, ...
  nationality: String              # astronomers
  yearFrom: Int                    # lower bound on year/launch_year/discovery_year
  yearTo: Int                      # upper bound
}

input Pagination {
  from: Int = 0    # offset (zero-based)
  size: Int = 10   # page size
}

enum ResourceType {
  CELESTIAL_OBJECTS
  MISSIONS
  OBSERVATIONS
  ASTRONOMERS
  PUBLICATIONS
}

type SearchResults {
  total: Int!
  hits: [SearchHit!]!
  interpretation: QueryInterpretationResult
}

type SearchHit {
  id: String!
  resourceType: ResourceType!
  score: Float!
  source: JSON!    # raw document fields from OpenSearch
}

type QueryInterpretationResult {
  rewrittenQuery: String     # cleaned query from LLM; original query if fallback
  extractedFilters: JSON     # filters parsed by LLM; empty if fallback
  searchMode: SearchMode!    # KEYWORD | SEMANTIC | HYBRID
}

enum SearchMode {
  KEYWORD    # BM25 full-text only
  SEMANTIC   # k-NN vector similarity only
  HYBRID     # BM25 + k-NN, scored by hybrid-pipeline (default)
}
```

## Example queries

### Bare search (cross-index, hybrid)

```graphql
query {
  search(input: { query: "Crab Nebula" }) {
    total
    hits {
      id
      resourceType
      score
      source
    }
    interpretation {
      rewrittenQuery
      searchMode
      extractedFilters
    }
  }
}
```

### Filtered search

```graphql
query {
  search(input: {
    query: "missions",
    filters: {
      agency: "NASA",
      yearFrom: 2000
    }
  }) {
    total
    hits { id resourceType score source }
  }
}
```

### Single-index search

```graphql
query {
  searchIndex(resourceType: ASTRONOMERS, input: { query: "pulsar" }) {
    total
    hits { id score source }
  }
}
```

### With pagination

```graphql
query {
  search(input: {
    query: "galaxy",
    pagination: { from: 0, size: 5 }
  }) {
    total
    hits { id resourceType score source }
  }
}
```

## curl equivalents

### Inline query

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ search(input: { query: \"Crab Nebula\" }) { total hits { id resourceType score } interpretation { rewrittenQuery searchMode } } }"}' \
  | jq .
```

### With variables

```bash
curl -s -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query($input: SearchInput!) { search(input: $input) { total hits { id resourceType score } } }",
    "variables": {
      "input": {
        "query": "missions",
        "filters": { "agency": "NASA", "yearFrom": 2000 }
      }
    }
  }' | jq .
```
