---
title: README Redesign
date: 2026-04-03
status: approved
---

## Summary

Redesign the project README to improve clarity, remove jargon, and sharpen the
layout. The current README uses a verbose "Why hybrid search?" explainer section,
has abbreviations throughout, and uses em dashes in prose.

## Design Decisions

### Layout

Centered header block (Option A): logo centered at top, followed by badges,
followed by the description. No decorative horizontal rules between the header
elements. Sections below follow a single horizontal rule separator.

Section order:

1. Header block (logo, badges, description)
2. Quick Start
3. Architecture
4. Stack
5. Docs

### Description (under badges)

Brief, accessible, no abbreviations, no em dashes:

> Search five astronomical datasets by keyword, by meaning, or in plain English.
> Embeddings and query parsing run entirely on your machine.

### Architecture Section

Replaces the current "Why hybrid search?" section. Title is "Architecture".

Intro paragraph (ELI5 opener, then technical payoff):

> If you search for "dying stars," you might miss a document that says "stellar
> remnants." nebullama-search finds both by running keyword and semantic search
> in parallel over five astronomical datasets. All embeddings and intent
> extraction run locally via Ollama, with no cloud dependencies.

Followed by an ASCII pipeline diagram showing all three flows:

```text
Ingest:  REST API  →  Ollama (nomic-embed-text)  →  OpenSearch write

Search:  GraphQL query
           ├── keyword search ──────────────┐
           └── vector similarity search ────┴── score merge → results

Intent:  natural language query  →  Ollama (mistral:7b)  →  structured filters
```

### Stack Table

Replace "BM25 + k-NN" with "keyword and vector search" in the OpenSearch row.
Replace "768-dim" with "768 dimensions".

### Writing Rules (apply throughout)

- No em dashes. Use commas, colons, or restructure the sentence.
- No abbreviations (BM25, k-NN, etc.). Write terms out in full.
- GraphQL, REST, and model names (nomic-embed-text, mistral:7b) are proper nouns
  and are not subject to the abbreviation rule.

## Sections Removed

- "Why hybrid search?" — replaced by the Architecture section
- Decorative `------` horizontal rules between header elements (logo, badges,
  and description are now one unified centered block with no separators between
  them; horizontal rules between major sections are kept)

## Sections Unchanged

- Quick Start (content and structure stay the same)
- Stack table (structure unchanged, one wording fix)
- Docs link
