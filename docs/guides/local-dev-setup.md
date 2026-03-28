# Local Dev Setup

Complete guide for running nebullama-search on your local machine.

## Prerequisites

- Java 21 (recommended: `brew install --cask temurin@21`)
- Docker and Docker Compose (Docker Desktop or Colima)
- `curl` (for health checks and the init script)
- 6 GB free disk space (Ollama models: ~4 GB combined)

## Step 1 — Start Infrastructure

Start OpenSearch, OpenSearch Dashboards, and Ollama:

```bash
docker-compose up -d
```

Wait for OpenSearch to become healthy (takes ~60 seconds on first run):

```bash
docker-compose ps
```

All three services should show `running` or `healthy`.

## Step 2 — Pull Ollama Models

Run the init script once to pull the required models:

```bash
./scripts/init.sh
```

This pulls `nomic-embed-text` (~274 MB) and `mistral:7b` (~4 GB). Skips any models already
present so it is safe to run again.

## Step 3 — Start the Service

```bash
cd service
./gradlew bootRun
```

The service starts on port 8080. Look for `Started NebullamaSearchApplication` in the logs.

## Verification

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP",...}`

Additional endpoints:

- GraphiQL: <http://localhost:8080/graphiql>
- OpenSearch Dashboards: <http://localhost:5601>
- OpenSearch API: <http://localhost:9200>

## Stopping

```bash
docker-compose down
```

Data is persisted in named volumes (`nebullama-opensearch-data`, `nebullama-ollama-data`).
To wipe volumes: `docker-compose down -v`

## Troubleshooting

### OpenSearch won't start

Check logs: `docker-compose logs opensearch`

Common cause: insufficient virtual memory. On Linux/WSL2:

```bash
sudo sysctl -w vm.max_map_count=262144
```

To make permanent, add `vm.max_map_count=262144` to `/etc/sysctl.conf`.

### Ollama models not pulling

Check Ollama is running: `curl http://localhost:11434/api/tags`

If the container is not healthy, check logs: `docker-compose logs ollama`

### Service fails to start

If OpenSearch is not yet healthy when the service starts, it will fail. Wait for
`docker-compose ps` to show OpenSearch as healthy, then re-run `./gradlew bootRun`.

### Port conflicts

Default ports: 9200 (OpenSearch), 9600 (OpenSearch perf analyser), 5601 (Dashboards), 11434
(Ollama), 8080 (service). If any are in use, update `docker-compose.yml` and/or
`service/src/main/resources/application.yml`.
