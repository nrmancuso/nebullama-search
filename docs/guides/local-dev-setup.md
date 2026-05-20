# Local Dev Setup

Complete guide for running nebullama-search on your local machine.

## Prerequisites

- Java 21 (recommended: `brew install --cask temurin@21`)
- Docker and Docker Compose (Docker Desktop or Colima)
- `curl` (for health checks and the init script)
- 6 GB free disk space (Ollama models: ~4 GB combined)

## Step 1 — Start the Stack

Start OpenSearch, OpenSearch Dashboards, Ollama, and the Spring service:

```bash
docker compose --profile local up -d --build
```

Wait for the core services to become healthy:

```bash
docker compose --profile local ps
```

`opensearch`, `ollama`, and `service` should show `running` or `healthy`. The local frontend
also starts on <http://localhost:5173> for browser-based searches.

## Step 2 — Pull Ollama Models

Run the init script once to pull the required models:

```bash
./scripts/init.sh
```

This pulls `nomic-embed-text` (~274 MB) and `mistral:7b` (~4 GB). Skips any models already
present so it is safe to run again.

## Step 3 — Verify the Service

The service starts on port 8080 inside Docker. If you want to watch startup logs:

```bash
docker compose --profile local logs -f service
```

## Verification

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP",...}`

Additional endpoints:

- GraphiQL: <http://localhost:8080/graphiql>
- Frontend demo: <http://localhost:5173>
- OpenSearch Dashboards: <http://localhost:5601>
- OpenSearch API: <http://localhost:9200>

## Stopping

```bash
docker compose --profile local down
```

Data is persisted in named volumes (`nebullama-opensearch-data`, `nebullama-ollama-data`).
To wipe volumes: `docker compose --profile local down -v`

## Troubleshooting

### OpenSearch won't start

Check logs: `docker compose --profile local logs opensearch`

Common cause: insufficient virtual memory. On Linux/WSL2:

```bash
sudo sysctl -w vm.max_map_count=262144
```

To make permanent, add `vm.max_map_count=262144` to `/etc/sysctl.conf`.

### Ollama models not pulling

Check Ollama is running: `curl http://localhost:11434/api/tags`

If the container is not healthy, check logs: `docker compose --profile local logs ollama`

### Service fails to start

Check service logs:

```bash
docker compose --profile local logs service
```

If OpenSearch or Ollama is not healthy yet, wait for `docker compose --profile local ps` to
show them as healthy and then restart the app container:

```bash
docker compose --profile local restart service
```

### Port conflicts

Default ports: 9200 (OpenSearch), 9600 (OpenSearch perf analyser), 5601 (Dashboards), 11434
(Ollama), 8080 (service). If any are in use, update `docker-compose.yml` and/or
`service/src/main/resources/application.yml`.
