# nebullama-search — Claude Code Guide

## Project

Hybrid search service over astronomy data using Spring Boot, OpenSearch (BM25 + k-NN),
and Ollama for local embeddings. Infrastructure runs in Docker Compose; the Spring Boot
service runs locally via Gradle.

## Naming

Always use `nebullama-search` (kebab-case). Never `NebullamaSearch` as a project name.

## Commit Message Format

Every commit must match: `^(Issue #[0-9]+|minor|doc|dependency|infra|ci): .+`
Maximum 72 characters. This is enforced by CI on all PRs.

Valid prefixes:

- `Issue #123:` — tracked work item
- `minor:` — small, untracked changes
- `doc:` — documentation only
- `dependency:` — dependency bumps (Dependabot uses this)
- `infra:` — infrastructure / Docker / config changes
- `ci:` — CI workflow changes

## CI

Three jobs run on every push to `main` and on all PRs (`.github/workflows/lint.yml`):

- **actionlint** — validates GitHub Actions workflow YAML
- **markdown** — `markdownlint-cli2` over all `**/*.md` files; config in `.markdownlint.json`
- **spellcheck** — `cspell` over `**/*.md`, `**/*.g4`, `**/*.java`

Commit message format is checked separately on PRs (`.github/workflows/commit-message.yml`).

Dependabot runs weekly for Gradle dependencies with the `dependency:` prefix.

## Markdown

All markdown must pass `markdownlint-cli2`. Config is in `.markdownlint.json` (MD013
line-length is disabled; MD024 allows duplicate headings in separate sections).

Run locally: `npx markdownlint-cli2 "**/*.md"`

## Tech Stack

- Java 21, Spring Boot 3.3.x, Gradle Kotlin DSL 8.x
- OpenSearch 2.13, opensearch-java 2.x
- Testcontainers (real OpenSearch in tests), WireMock (stubbed Ollama)
- GraphQL API for search, REST API for ingest
- BM25 + k-NN hybrid search; Ollama `nomic-embed-text` for embeddings

## Testing

Two-tier: unit tests (fast, no containers) and integration tests (Testcontainers + WireMock).
Do not mock OpenSearch in integration tests — use real containers.

## Docker

Always pin Docker image versions (e.g. `ollama/ollama:0.18.3`, not `ollama/ollama:latest`).

## Plans

Implementation plans live in `docs/plans/`. Phase files follow the naming pattern
`YYYY-MM-DD-phase<N>-<name>.md`. Use checkbox syntax (`- [ ]`) for task tracking.

## Docs

When completing a feature, check whether any placeholder docs in `docs/` should be
updated to reflect what was built. Placeholders are marked with `> 🚧`. If a section
is now implemented, replace the placeholder with real content.
