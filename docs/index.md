# nebullama-search

![nebullama icon](assets/nebullama-icon.svg)

A local-dev hybrid search service over astronomy data — a learning project for vector search, OpenSearch k-NN, and LLM-powered intent extraction.

---

## Sections

- [[architecture/overview|Architecture Overview]]
- [[concepts/hybrid-search|Hybrid Search]]
- [[concepts/vector-embeddings|Vector Embeddings]]
- [[concepts/intent-extraction|Intent Extraction]]
- [[guides/local-dev-setup|Local Dev Setup]]
- [[guides/data-ingestion|Data Ingestion]]
- [[guides/running-searches|Running Searches]]
- [[api-reference/graphql-schema|GraphQL Schema]]
- [[api-reference/ingest-rest-api|Ingest REST API]]
- [[deployment/aws|AWS Deployment]]

---

## Stack

| Concern | Technology |
| --- | --- |
| Service | Spring Boot 3.3, Java 21 |
| Search | OpenSearch 2.x (BM25 + k-NN) |
| Embeddings | Ollama + nomic-embed-text (768-dim) |
| Intent extraction | Ollama + mistral:7b |
| API | GraphQL (Spring for GraphQL) |
| Ingest | REST (Spring MVC) |
| Infrastructure | Docker Compose |
