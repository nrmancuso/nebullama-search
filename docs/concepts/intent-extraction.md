# Intent Extraction

## What it does

When a user types a natural language query like "NASA missions to Jupiter after 2000", a naive
keyword search would match any document containing those words. Intent extraction uses an LLM to
parse the query into structured parts:

- `cleanedQuery`: the core search terms stripped of meta-instructions, e.g. "Jupiter missions"
- `resourceTypeHints`: which indexes are likely relevant, e.g. `["missions"]`
- `filters`: extracted field filters, e.g. `{ "agency": "NASA", "yearFrom": 2000 }`
- `searchMode`: whether to use keyword, semantic, or hybrid search

These structured parts are then used to build a more precise OpenSearch query instead of running
a broad cross-field text search.

## The prompt contract

`IntentExtractionService` sends two messages to Ollama's `/api/chat` endpoint:

**System message** (abbreviated):

> You are a search query parser for an astronomy database. Given a user's search query,
> respond with ONLY a valid JSON object (no explanation, no markdown fences, no preamble)
> with exactly these fields: ...

Requiring "ONLY a valid JSON object" is essential. Without this constraint, LLMs tend to wrap
the JSON in prose or markdown fences, which breaks JSON parsing.

**User message:** the raw query string as-is.

**Expected response:**

```json
{
  "cleanedQuery": "Jupiter missions",
  "resourceTypeHints": ["missions"],
  "filters": {
    "agency": "NASA",
    "yearFrom": 2000
  },
  "searchMode": "hybrid"
}
```

The model used is configured via `ollama.intent-model` in `application.yml` (default: `mistral`).

## Fallback behavior

Intent extraction has a hard timeout (`search.intent-extraction.timeout-ms`, default 3000ms).
If any of the following happen, the service falls back silently to a bare hybrid search with the
original query string:

| Situation | Fallback trigger |
| --- | --- |
| `search.intent-extraction.enabled: false` | Immediate fallback, no HTTP call |
| Ollama connect/read timeout | `OllamaChatTimeoutException` caught |
| HTTP error from Ollama | `OllamaChatException` caught |
| LLM returns non-JSON text | JSON parse exception caught |
| Any other exception | General `Exception` caught |

The fallback response is:

```text
QueryInterpretation(
    rewrittenQuery = rawQuery,
    extractedFilters = {},
    searchMode = HYBRID
)
```

This means the search pipeline always continues; no exception propagates to the caller.

## Disabling intent extraction

To send a search request without LLM interpretation:

```yaml
search:
  intent-extraction:
    enabled: false
```

Or pass as a Spring property:

```bash
./gradlew bootRun --args='--search.intent-extraction.enabled=false'
```

With intent extraction disabled, the query is sent as-is to the search service with
`searchMode: HYBRID` and an empty filter map.
