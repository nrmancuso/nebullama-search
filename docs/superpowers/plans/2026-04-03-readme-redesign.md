# README Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `README.md` with a cleaner layout, an accessible description,
and a new Architecture section replacing the verbose "Why hybrid search?" explainer.

**Architecture:** Single file edit to `README.md`. No code changes. The header
block is restructured (logo then badges then description, no internal dividers), the
"Why hybrid search?" section is replaced with a concise "Architecture" section
containing an ASCII pipeline diagram, and the Stack table has two wording fixes.

**Tech Stack:** Markdown, markdownlint-cli2 (linting)

---

## Tasks

### Task 1: Update the header block

The current header order is: logo, `------`, description, badges, `------`.
The new order is: logo, badges, description, with a single `------` after
all three and no dividers between them.

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Replace everything before `## Why hybrid search?`**

  Delete from line 1 through the blank line immediately before `## Why hybrid search?`.
  Replace with the block below. The badges block now comes before the description,
  and there is only one `------` at the end.

  Logo block (unchanged, keep as-is):

  ```markdown
  <p align="center">
    <img src="assets/nebullama/nebullama.svg" alt="nebullama-search" width="500">
  </p>
  ```

  Badges block (move above description — currently it appears below):

  ```markdown
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21-blue?style=flat-square" alt="Java 21">
    <img src="https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?style=flat-square" alt="Spring Boot 3.3">
    <img src="https://img.shields.io/badge/OpenSearch-2.x-orange?style=flat-square" alt="OpenSearch 2.x">
    <img src="https://img.shields.io/badge/Ollama-local-lightgrey?style=flat-square" alt="Ollama">
  </p>
  ```

  New description block (replaces the old `<em>` tagline):

  ```markdown
  <p align="center">
    <em>
    Search five astronomical datasets by keyword, by meaning, or in plain English.
    Embeddings and query parsing run entirely on your machine.
    </em>
  </p>

  ---
  ```

- [ ] **Step 2: Run markdownlint**

  ```bash
  npx markdownlint-cli2 "README.md"
  ```

  Expected: `Summary: 0 error(s)`

- [ ] **Step 3: Commit**

  ```bash
  git add README.md
  git commit -m "Issue #3: update README header block layout and description"
  ```

---

### Task 2: Replace "Why hybrid search?" with "Architecture"

The "Why hybrid search?" section is deleted and replaced with "Architecture",
which moves to appear after "Quick Start". A `---` divider is added after
Quick Start to separate it from Architecture.

**Files:**

- Modify: `README.md`

- [ ] **Step 1: Delete the "Why hybrid search?" section**

  Remove everything from `## Why hybrid search?` through the blank line
  immediately before `## Quick Start`.

- [ ] **Step 2: Add a `---` divider after the Quick Start section**

  The Quick Start section currently ends with no divider before `## Stack`.
  Add a `---` on a blank line after the last line of the Quick Start section
  and before `## Stack`.

- [ ] **Step 3: Insert the Architecture section between Quick Start and Stack**

  After the `---` added in Step 2, insert the following. The section heading:

  ```markdown
  ## Architecture
  ```

  The intro paragraph:

  ```markdown
  If you search for "dying stars," you might miss a document that says "stellar
  remnants." nebullama-search finds both by running keyword and semantic search
  in parallel over five astronomical datasets. All embeddings and intent
  extraction run locally via Ollama, with no cloud dependencies.
  ```

  The ASCII pipeline diagram (use a fenced `text` code block):

  ```text
  Ingest:  REST API  ->  Ollama (nomic-embed-text)  ->  OpenSearch write

  Search:  GraphQL query
             +-- keyword search ---------------+
             +-- vector similarity search -----+-- score merge -> results

  Intent:  natural language query  ->  Ollama (mistral:7b)  ->  structured filters
  ```

  A closing `---` divider after the diagram and before `## Stack`.

- [ ] **Step 4: Verify section order**

  The file sections must now appear in this order:

  1. Header block (logo, badges, description)
  2. `---`
  3. `## Quick Start`
  4. `---`
  5. `## Architecture`
  6. `---`
  7. `## Stack`
  8. `## Docs`

- [ ] **Step 5: Run markdownlint**

  ```bash
  npx markdownlint-cli2 "README.md"
  ```

  Expected: `Summary: 0 error(s)`

- [ ] **Step 6: Commit**

  ```bash
  git add README.md
  git commit -m "Issue #3: replace Why hybrid search section with Architecture"
  ```

---

### Task 3: Update the Stack table

Two rows in the Stack table still contain abbreviated terms that need to be
written out in full.

**Files:**

- Modify: `README.md` (the `## Stack` table)

- [ ] **Step 1: Fix the two rows**

  | Row | Old value | New value |
  | --- | --- | --- |
  | Search | `OpenSearch 2.x (BM25 + k-NN)` | `OpenSearch 2.x (keyword and vector search)` |
  | Embeddings | `Ollama + nomic-embed-text (768-dim)` | `Ollama + nomic-embed-text (768 dimensions)` |

  The full updated table:

  ```markdown
  | Concern | Technology |
  | --- | --- |
  | Service | Spring Boot 3.3, Java 21 |
  | Search | OpenSearch 2.x (keyword and vector search) |
  | Embeddings | Ollama + nomic-embed-text (768 dimensions) |
  | Intent extraction | Ollama + mistral:7b |
  | API | GraphQL (Spring for GraphQL) |
  | Ingest | REST (Spring MVC) |
  | Infrastructure | Docker Compose |
  ```

- [ ] **Step 2: Run markdownlint**

  ```bash
  npx markdownlint-cli2 "README.md"
  ```

  Expected: `Summary: 0 error(s)`

- [ ] **Step 3: Commit**

  ```bash
  git add README.md
  git commit -m "Issue #3: update Stack table wording"
  ```
