# Running Searches Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder running-searches guide with a first-run walkthrough for GraphiQL and `curl` that uses meaningful, stable example queries.

**Architecture:** Update a single Markdown guide in place. Keep the content task-oriented: prerequisites, GraphiQL workflow, four copy-pasteable examples with `curl` equivalents, a practical explanation of `interpretation`, and a raw-query debugging section for disabling intent extraction.

**Tech Stack:** Markdown, Spring GraphQL endpoint at `/graphql`, GraphiQL at `/graphiql`, markdownlint-cli2

---

## Task 1: Rewrite the guide content

**Files:**

- Modify: `docs/guides/running-searches.md`

- [ ] **Step 1: Replace the placeholder structure with a first-run walkthrough**

Write sections for prerequisites, opening GraphiQL, a first query, filtered search, single-index search, pagination, response interpretation, `curl` usage, disabling intent extraction, and troubleshooting.

- [ ] **Step 2: Use stable example queries grounded in seeded data**

Use examples centered on entities that exist in the current seed fixtures and are likely to return meaningful output:

```text
Crab Nebula
NASA missions
Andromeda
star
```

Avoid promises about exact ranking unless the example is a direct text match.

- [ ] **Step 3: Add a `curl` equivalent for each GraphQL example**

For each GraphiQL example, add a matching `curl -s -X POST http://localhost:8080/graphql` snippet that is copy-pasteable and ends with `| jq .`.

- [ ] **Step 4: Explain `interpretation` in practical terms**

Document:

```text
rewrittenQuery
searchMode
extractedFilters
```

Explain what a developer should inspect in each field and what fallback behavior looks like when intent extraction is disabled or fails.

## Task 2: Verify the guide format and repo expectations

**Files:**

- Modify: `docs/guides/running-searches.md`

- [ ] **Step 1: Run markdown lint against the updated guide**

Run: `npx markdownlint-cli2 docs/guides/running-searches.md`

Expected: no lint errors

- [ ] **Step 2: Review the final guide for acceptance criteria**

Confirm the guide:

```text
- tells a new developer how to open GraphiQL at http://localhost:8080/graphiql
- includes annotated bare, filtered, single-index, and paginated examples
- includes curl equivalents for every GraphQL example
- explains QueryInterpretation fields
- shows how to disable intent extraction with search.intent-extraction.enabled=false
```

- [ ] **Step 3: Commit with the repo-required docs prefix**

```bash
git add docs/guides/running-searches.md docs/superpowers/plans/2026-04-04-issue15-running-searches-guide.md
git commit -m "Issue #15: write running searches guide"
```
