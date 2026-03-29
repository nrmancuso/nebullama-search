#!/usr/bin/env python3
"""
fetch_seed_data.py — fetch ~200 real astronomy documents from public APIs.

Writes five JSON files to data/ (~40 docs each):
  seed_celestial_objects.json
  seed_missions.json
  seed_observations.json
  seed_astronomers.json
  seed_publications.json

Also writes 3-doc test fixtures to service/src/test/resources/seed/.

Usage:
  pip install -r scripts/requirements.txt
  ADS_TOKEN=<your_token> python scripts/fetch_seed_data.py

ADS tokens are free: https://ui.adsabs.harvard.edu/user/settings/token
"""

import json
import os
import re
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

import requests
import wikipediaapi

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
TEST_SEED_DIR = (
    Path(__file__).resolve().parent.parent
    / "service"
    / "src"
    / "test"
    / "resources"
    / "seed"
)
DOCS_PER_INDEX = 40
TEST_DOCS_PER_INDEX = 3

ADS_TOKEN = os.environ.get("ADS_TOKEN", "")

ANCHOR_OBJECTS = [
    "Crab Nebula",
    "Andromeda Galaxy",
    "Cygnus X-1",
    "Orion Nebula",
    "Sagittarius A*",
    "Pleiades",
    "Centaurus A",
    "Omega Nebula",
    "Eagle Nebula",
    "Whirlpool Galaxy",
    "Horsehead Nebula",
    "Vela Pulsar",
]

ANCHOR_MISSIONS = [
    "Hubble Space Telescope",
    "James Webb Space Telescope",
    "Voyager 1",
    "Cassini",
    "Chandra X-ray Observatory",
    "Kepler space telescope",
    "Spitzer Space Telescope",
    "Herschel Space Observatory",
    "XMM-Newton",
    "Fermi Gamma-ray Space Telescope",
    "TESS",
    "Gaia (spacecraft)",
]

ANCHOR_ASTRONOMERS = [
    "Jocelyn Bell Burnell",
    "Carl Sagan",
    "Vera Rubin",
    "Edwin Hubble",
    "Cecilia Payne-Gaposchkin",
    "Subrahmanyan Chandrasekhar",
    "Annie Jump Cannon",
    "William Herschel",
    "Henrietta Swan Leavitt",
    "Georges Lemaître",
    "Jan Oort",
    "Fred Hoyle",
]

ADS_SEARCH_TERMS = [
    "Crab Nebula pulsar",
    "Andromeda Galaxy M31",
    "Cygnus X-1 black hole",
    "Hubble Space Telescope deep field",
    "James Webb Space Telescope JWST infrared",
    "Voyager 1 heliosphere",
    "Cassini Saturn rings",
    "Chandra X-ray Observatory",
    "pulsars neutron stars",
    "dark matter rotation curves",
    "cosmic microwave background",
    "exoplanet atmosphere spectroscopy",
]

WIKI = wikipediaapi.Wikipedia(
    user_agent="nebullama-search/1.0 (learning project; contact: nebullama@example.com)",
    language="en",
    extract_format=wikipediaapi.ExtractFormat.WIKI,
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def get_wiki_summary(title: str) -> str:
    """Return the first ~3 paragraphs of a Wikipedia article as plain text."""
    page = WIKI.page(title)
    if not page.exists():
        return ""
    text = page.text
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    return "\n\n".join(paragraphs[:3])


def save(directory: Path, filename: str, docs: list[dict]) -> None:
    """Write a list of documents as a pretty-printed JSON array."""
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / filename
    with open(path, "w", encoding="utf-8") as f:
        json.dump(docs, f, indent=2, ensure_ascii=False)
    print(f"  Saved {len(docs)} docs -> {path}")


def dedupe(docs: list[dict], key: str) -> list[dict]:
    """Deduplicate documents by a given field."""
    seen: set[str] = set()
    out: list[dict] = []
    for d in docs:
        v = d.get(key)
        if v and v not in seen:
            seen.add(v)
            out.append(d)
    return out


# ---------------------------------------------------------------------------
# Index fetchers (implemented in Tasks 2-6)
# ---------------------------------------------------------------------------


def fetch_celestial_objects() -> list[dict]:
    raise NotImplementedError


def fetch_missions() -> list[dict]:
    raise NotImplementedError


def fetch_observations() -> list[dict]:
    raise NotImplementedError


def fetch_astronomers() -> list[dict]:
    raise NotImplementedError


def fetch_publications() -> list[dict]:
    raise NotImplementedError


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main() -> None:
    if not ADS_TOKEN:
        print("ERROR: ADS_TOKEN environment variable is required.", file=sys.stderr)
        print(
            "Get a free token: https://ui.adsabs.harvard.edu/user/settings/token",
            file=sys.stderr,
        )
        sys.exit(1)

    print("=== nebullama-search seed data fetch ===\n")

    fetchers = [
        ("celestial_objects", fetch_celestial_objects, "seed_celestial_objects.json"),
        ("missions", fetch_missions, "seed_missions.json"),
        ("observations", fetch_observations, "seed_observations.json"),
        ("astronomers", fetch_astronomers, "seed_astronomers.json"),
        ("publications", fetch_publications, "seed_publications.json"),
    ]

    for index_name, fetcher, filename in fetchers:
        print(f"[{index_name}] Fetching...")
        try:
            docs = fetcher()
            print(f"[{index_name}] Got {len(docs)} docs")
            save(DATA_DIR, filename, docs)
            save(TEST_SEED_DIR, filename, docs[:TEST_DOCS_PER_INDEX])
        except NotImplementedError:
            print(f"[{index_name}] Not yet implemented -- skipping")
        except Exception as exc:
            print(f"[{index_name}] ERROR: {exc}", file=sys.stderr)
        print()

    print("Done.")


if __name__ == "__main__":
    main()
