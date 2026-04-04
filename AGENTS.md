# Repository Guidelines

## Project Structure & Module Organization

`nebullama-search` is a Gradle multi-module project. `service/` contains the Spring Boot app, with Java sources in `service/src/main/java/com/example/nebullamasearch`, GraphQL schema and OpenSearch mappings in `service/src/main/resources`, and unit tests in `service/src/test/java`. `integration-tests/` holds end-to-end tests that run against the live Docker Compose stack. Docs live in `docs/`, helper scripts in `scripts/`, and repository assets in `assets/`.

## Build, Test, and Development Commands

- `./gradlew :service:test` runs the service unit test suite.
- `./gradlew :integration-tests:test` runs integration tests when the stack is already up.
- `./scripts/run-integration-tests.sh` starts infrastructure, seeds data, and runs the end-to-end suite.
- `./gradlew spotlessCheck` verifies Java formatting across modules.
- `./gradlew :service:checkstyleMain :service:checkstyleTest` runs Java style checks.
- `./gradlew :service:spotbugsMain :service:spotbugsTest` runs static analysis.
- `docker compose --profile local up -d` starts OpenSearch and Ollama for local work.

## Coding Style & Naming Conventions

Use Java 21 with Google Java Style via Spotless. Format with `./gradlew spotlessApply` before committing. Follow the existing package layout under `com.example.nebullamasearch`. Use explicit local variable types, declare local variables as `final`, and do not mark method parameters `final`. Keep project naming in docs and scripts as `nebullama-search`.

## Testing Guidelines

Unit tests live beside the service module and typically use `*Test` names, for example `SearchQueryBuilderTest`. Integration tests live in `integration-tests/src/test/java/.../it` and use `*IT`, for example `HybridSearchIT`. Prefer real integrations over mocks for OpenSearch-facing behavior; the repo already uses Testcontainers, WireMock, and the full Docker stack where appropriate.

## Commit & Pull Request Guidelines

Commit subjects must match `^(Issue #[0-9]+|minor|doc|dependency|infra|ci): .+` and stay within 72 characters. Recent examples include `minor: integration test improvements` and `doc: tweak readme a bit`. PRs should describe behavior changes, reference the relevant issue when there is one, and include request/response examples or screenshots when UI or API docs change.

## Docs, Markdown, and Configuration

Run `npx markdownlint-cli2 "**/*.md"` after editing Markdown. If a feature changes documented behavior, update the matching files in `docs/` and replace any `> 🚧` placeholders that are no longer accurate. Pin Docker image versions; do not switch images to floating tags such as `latest`.
