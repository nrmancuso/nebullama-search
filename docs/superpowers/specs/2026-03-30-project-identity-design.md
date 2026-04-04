---
name: Project Identity — Logo & README
description: Design spec for issue #3 — PNG icon, root README, docs/index.md update
type: project
---

# Project Identity — Logo & README

## Goal

Implement issue #3: wire the nebullama mascot PNG into the root README and the docs vault
home page, producing a complete README with icon, badges, architecture diagram, and quick
start.

## Scope

Three changes:

1. **Rename PNG** — `assets/nebullama/llama-space-art-1774847580037.png` →
   `assets/nebullama/nebullama-icon.png` (clean stable path, drops timestamp)
2. **Rewrite `README.md`** — icon, shields.io badges, Mermaid architecture diagram,
   quick-start steps, stack table, link to docs vault
3. **Update `docs/index.md`** — change broken `.svg` reference to `.png`

No SVG is created. No `docs/assets/README.md` is created.

## README.md Design

### Layout

- Icon (`assets/nebullama/nebullama-icon.png`, 120px wide, right-aligned) floats beside the heading
- Four shields.io badges: Java 21, Spring Boot 3.3, OpenSearch 2.x, Ollama
- One-line project description
- `## Architecture` — Mermaid flowchart (left-to-right), show both ingestion and searching
- `## Quick Start` — condensed 3-step version of `docs/guides/local-dev-setup.md`
- `## Stack` — table matching `docs/index.md` stack table
- `## Docs` — link to `docs/index.md`

### Mermaid Architecture Diagram

Left-to-right flow:

```text
Client (GraphQL / REST)
  → Spring Boot Service
      → OpenSearch  (BM25 keyword + k-NN vector hybrid)
      → Ollama      (nomic-embed-text embeddings)
```

### Badges

| Badge | Shield label |
| --- | --- |
| Java 21 | `Java-21-blue` |
| Spring Boot 3.3 | `Spring Boot-3.3-brightgreen` |
| OpenSearch 2.x | `OpenSearch-2.x-orange` |
| Ollama | `Ollama-local-lightgrey` |

All use `style=flat-square`.

### Quick Start (3 steps)

1. Start Docker Compose (`docker-compose up -d && ./scripts/init.sh`)
2. Run the service (`cd service && ./gradlew bootRun`)
3. Verify (`curl http://localhost:8080/actuator/health`)

## docs/index.md Change

Single line edit: `assets/nebullama-icon.svg` → `assets/nebullama/nebullama-icon.png`

## Acceptance Criteria

- `README.md` renders on GitHub: icon visible, badges load, Mermaid renders, quick start
  works
- `docs/index.md` icon reference resolves to the PNG
- No broken image links in either file
- All markdown passes `markdownlint-cli2`
