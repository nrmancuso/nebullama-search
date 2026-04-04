#!/usr/bin/env python3
"""
Populate committed seed JSON files with precomputed embeddings from Ollama.

By default this updates:
  - data/seed_*.json
  - service/src/test/resources/seed/seed_*.json

Existing valid embeddings are reused unless --force is passed.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SEED_DIRS = [
    ROOT / "data",
    ROOT / "service" / "src" / "test" / "resources" / "seed",
]
RESOURCE_TYPES = [
    "celestial_objects",
    "missions",
    "observations",
    "astronomers",
    "publications",
]
PRIMARY_TEXT_FIELD = {
    "celestial_objects": "description",
    "missions": "description",
    "observations": "notes",
    "astronomers": "biography",
    "publications": "abstract",
}
EMBEDDING_DIMENSIONS = 768


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--model", default="nomic-embed-text")
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def embed_text(ollama_url: str, model: str, text: str) -> list[float]:
    payload = json.dumps({"model": model, "prompt": text}).encode("utf-8")
    request = urllib.request.Request(
        f"{ollama_url}/api/embeddings",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        body = response.read().decode("utf-8")
    parsed = json.loads(body)
    embedding = parsed.get("embedding")
    if not isinstance(embedding, list) or len(embedding) != EMBEDDING_DIMENSIONS:
        raise ValueError(
            f"Expected {EMBEDDING_DIMENSIONS}-value embedding, got {type(embedding).__name__}"
        )
    return [float(value) for value in embedding]


def has_valid_embedding(doc: dict) -> bool:
    embedding = doc.get("embedding")
    return (
        isinstance(embedding, list)
        and len(embedding) == EMBEDDING_DIMENSIONS
        and all(isinstance(value, (int, float)) for value in embedding)
    )


def update_seed_file(
    path: Path, resource_type: str, ollama_url: str, model: str, force: bool
) -> None:
    docs = json.loads(path.read_text(encoding="utf-8"))
    primary_text_field = PRIMARY_TEXT_FIELD[resource_type]
    updated = 0
    reused = 0

    for index, doc in enumerate(docs, start=1):
        if has_valid_embedding(doc) and not force:
            reused += 1
            continue

        text = str(doc.get(primary_text_field, ""))
        if not text:
            raise ValueError(
                f"{path}: doc {index} is missing primary text field '{primary_text_field}'"
            )
        doc["embedding"] = embed_text(ollama_url, model, text)
        updated += 1

    path.write_text(
        json.dumps(docs, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(f"{path}: updated={updated} reused={reused} total={len(docs)}")


def main() -> int:
    args = parse_args()
    try:
        for seed_dir in SEED_DIRS:
            for resource_type in RESOURCE_TYPES:
                path = seed_dir / f"seed_{resource_type}.json"
                if not path.exists():
                    raise FileNotFoundError(path)
                update_seed_file(path, resource_type, args.ollama_url, args.model, args.force)
    except (OSError, ValueError, urllib.error.URLError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
