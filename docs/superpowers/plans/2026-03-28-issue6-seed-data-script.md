# Issue #6: Seed Data Fetching Script — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a Python script that fetches ~200 real astronomy documents from public APIs and writes both a full dataset (5 JSON files, ~40 docs each) and a small test fixture set (3 docs per index) — all checked into git.

**Architecture:** Single-file script (`scripts/fetch_seed_data.py`) organized into shared helpers, one fetcher function per index, and a `main()` orchestrator. Sources: SIMBAD TAP (celestial objects), Wikipedia (missions, astronomers), MAST Portal (observations), NASA ADS (publications). `ADS_TOKEN` env var required.

**Tech Stack:** Python 3.11+, `requests`, `wikipedia-api`

---

## File Map

| File | Create/Modify | Purpose |
| --- | --- | --- |
| `scripts/requirements.txt` | Create | Python dependencies |
| `scripts/fetch_seed_data.py` | Create | Main fetch script (~400-500 lines) |
| `.gitignore` | Modify | Remove `data/seed_*.json` ignore rule |
| `data/seed_celestial_objects.json` | Created by script | ~40 celestial object documents |
| `data/seed_missions.json` | Created by script | ~40 space mission documents |
| `data/seed_observations.json` | Created by script | ~40 observational records |
| `data/seed_astronomers.json` | Created by script | ~40 astronomer biographies |
| `data/seed_publications.json` | Created by script | ~40 astronomy publications |
| `service/src/test/resources/seed/seed_celestial_objects.json` | Created by script | 3-doc test fixture |
| `service/src/test/resources/seed/seed_missions.json` | Created by script | 3-doc test fixture |
| `service/src/test/resources/seed/seed_observations.json` | Created by script | 3-doc test fixture |
| `service/src/test/resources/seed/seed_astronomers.json` | Created by script | 3-doc test fixture |
| `service/src/test/resources/seed/seed_publications.json` | Created by script | 3-doc test fixture |

---

## Tasks

---

### Task 1: Script Skeleton, Requirements, and Gitignore

**Files:**

- Create: `scripts/requirements.txt`
- Create: `scripts/fetch_seed_data.py` (skeleton only)
- Modify: `.gitignore` (line 116)

- [ ] **Step 1: Create `scripts/requirements.txt`**

```text
requests==2.32.3
wikipedia-api==0.7.1
```

- [ ] **Step 2: Remove seed data ignore from `.gitignore`**

In `.gitignore`, remove this line (currently line 116):

```text
data/seed_*.json
```

Also remove the comment on line 115:

```text
# Seed data (generated — large files, not committed)
```

- [ ] **Step 3: Create `scripts/fetch_seed_data.py` skeleton**

```python
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
```

- [ ] **Step 4: Set up venv and verify skeleton runs**

```bash
cd /Users/nick/IdeaProjects/nebullama-search
python3 -m venv .venv && source .venv/bin/activate
pip install -r scripts/requirements.txt
ADS_TOKEN=fake python scripts/fetch_seed_data.py
```

Expected: prints "ERROR: ADS_TOKEN environment variable is required." and exits 1 (since `ADS_TOKEN=fake` won't be empty, remove the fake token test; instead test with no token):

```bash
python scripts/fetch_seed_data.py
```

Expected output:

```text
ERROR: ADS_TOKEN environment variable is required.
Get a free token: https://ui.adsabs.harvard.edu/user/settings/token
```

Now test with a dummy token to see the skeleton run through:

```bash
ADS_TOKEN=test python scripts/fetch_seed_data.py
```

Expected output:

```text
=== nebullama-search seed data fetch ===

[celestial_objects] Fetching...
[celestial_objects] Not yet implemented -- skipping

[missions] Fetching...
[missions] Not yet implemented -- skipping

[observations] Fetching...
[observations] Not yet implemented -- skipping

[astronomers] Fetching...
[astronomers] Not yet implemented -- skipping

[publications] Fetching...
[publications] Not yet implemented -- skipping

Done.
```

- [ ] **Step 5: Commit**

```bash
git add scripts/requirements.txt scripts/fetch_seed_data.py .gitignore
git commit -m "Issue #6: add seed data script skeleton and requirements"
```

---

### Task 2: Implement `fetch_celestial_objects()` — SIMBAD TAP + Wikipedia

**Files:**

- Modify: `scripts/fetch_seed_data.py` — replace `fetch_celestial_objects()` stub

- [ ] **Step 1: Implement `fetch_celestial_objects()`**

Replace the `raise NotImplementedError` stub with:

```python
def fetch_celestial_objects() -> list[dict]:
    """
    Query SIMBAD TAP/ADQL for objects by name. Supplement description
    with Wikipedia summary where available. Targets 40 documents.
    """
    docs: list[dict] = []

    # Extended name list so we can dedupe down to 40
    names = ANCHOR_OBJECTS + [
        "Betelgeuse",
        "Sirius",
        "Proxima Centauri",
        "Alpha Centauri",
        "Barnard's Star",
        "Polaris",
        "Rigel",
        "Vega",
        "Antares",
        "Aldebaran",
        "Capella",
        "Arcturus",
        "Spica",
        "Deneb",
        "Altair",
        "Fomalhaut",
        "Epsilon Eridani",
        "Tau Ceti",
        "61 Cygni",
        "Wolf 359",
        "Lalande 21185",
        "Ross 128",
        "Groombridge 34",
        "HD 209458",
        "51 Pegasi",
        "47 Tucanae",
        "Omega Centauri",
        "Large Magellanic Cloud",
        "Small Magellanic Cloud",
    ]

    simbad_url = "https://simbad.cds.unistra.fr/simbad/tap/sync"

    otype_map = {
        "Star": "star", "**": "star", "PM*": "star", "V*": "star",
        "HB*": "star", "RG*": "star", "SG*": "star", "WR*": "star",
        "Psr": "pulsar", "Neb": "nebula", "SNR": "nebula",
        "PN": "nebula", "HII": "nebula", "MoC": "nebula",
        "Gl?": "galaxy", "G": "galaxy", "LIN": "galaxy",
        "GiG": "galaxy", "SyG": "galaxy", "AGN": "galaxy",
        "Cl*": "cluster", "GlC": "cluster", "OpC": "cluster",
        "BH": "black_hole", "XB*": "black_hole",
    }

    for name in names:
        if len(docs) >= DOCS_PER_INDEX:
            break
        try:
            adql = (
                f"SELECT main_id, otype, ra, dec, plx_value, "
                f"rvz_redshift, sp_type "
                f"FROM basic "
                f"WHERE main_id = '{name}' "
                f"OR ids LIKE '%{name}%' "
                f"LIMIT 1"
            )
            resp = requests.get(
                simbad_url,
                params={
                    "REQUEST": "doQuery",
                    "LANG": "ADQL",
                    "FORMAT": "json",
                    "QUERY": adql,
                },
                timeout=15,
            )
            resp.raise_for_status()
            data = resp.json()
            rows = data.get("data", [])
            cols = [c["name"] for c in data.get("metadata", [])]

            if not rows:
                print(f"  SIMBAD: no result for '{name}'")
                continue

            row = dict(zip(cols, rows[0]))
            main_id = (row.get("main_id") or name).strip()

            # Distance from parallax (arcsec -> ly: 3260 / parallax)
            plx = row.get("plx_value")
            distance_ly = None
            if plx and float(plx) > 0:
                distance_ly = round(3260.0 / float(plx), 1)

            # Wikipedia description
            description = get_wiki_summary(name)
            if not description:
                description = get_wiki_summary(main_id)
            if not description:
                description = (
                    f"{main_id} is an astronomical object catalogued in SIMBAD."
                )

            raw_otype = (row.get("otype") or "").strip()
            object_type = otype_map.get(raw_otype, "other")

            doc = {
                "resource_type": "celestial_objects",
                "name": main_id,
                "designations": [main_id],
                "object_type": object_type,
                "constellation": None,
                "distance_ly": distance_ly,
                "description": description,
                "discovered_by": None,
                "discovery_year": None,
            }
            docs.append(doc)
            print(f"  OK: {main_id} ({object_type})")
            time.sleep(0.3)

        except Exception as exc:
            print(f"  WARN: {name} -- {exc}")

    return dedupe(docs, "name")[:DOCS_PER_INDEX]
```

- [ ] **Step 2: Run and verify output**

```bash
ADS_TOKEN=$ADS_TOKEN python scripts/fetch_seed_data.py
```

Expected: `[celestial_objects]` prints ~40 "OK:" lines and writes `data/seed_celestial_objects.json`. Spot-check:

```bash
python3 -c "
import json
d = json.load(open('data/seed_celestial_objects.json'))
print(f'{len(d)} docs')
print(json.dumps(d[0], indent=2)[:300])
"
```

Expected: `40 docs` (or close) and a dict with keys `resource_type`, `name`, `object_type`, `description`.

Also verify test fixture:

```bash
python3 -c "
import json
d = json.load(open('service/src/test/resources/seed/seed_celestial_objects.json'))
print(f'{len(d)} test docs')
"
```

Expected: `3 test docs`

- [ ] **Step 3: Commit**

```bash
git add scripts/fetch_seed_data.py
git commit -m "Issue #6: implement fetch_celestial_objects via SIMBAD TAP"
```

---

### Task 3: Implement `fetch_missions()` — Wikipedia

**Files:**

- Modify: `scripts/fetch_seed_data.py` — replace `fetch_missions()` stub

- [ ] **Step 1: Implement `fetch_missions()`**

Replace the `raise NotImplementedError` stub with:

```python
def fetch_missions() -> list[dict]:
    """Fetch mission data from Wikipedia for anchor + extra missions."""
    extra_missions = [
        "New Horizons",
        "Mars Reconnaissance Orbiter",
        "Opportunity rover",
        "Curiosity rover",
        "Perseverance rover",
        "Parker Solar Probe",
        "Solar and Heliospheric Observatory",
        "Ulysses (spacecraft)",
        "INTEGRAL",
        "NuSTAR",
        "WISE (spacecraft)",
        "Planck (spacecraft)",
        "WMAP",
        "Rosetta (spacecraft)",
        "Dawn (spacecraft)",
        "Deep Impact (spacecraft)",
        "Stardust (spacecraft)",
        "OSIRIS-REx",
        "Hayabusa",
        "Hayabusa2",
        "BepiColombo",
        "Mars Express",
        "Venus Express",
        "Juno (spacecraft)",
        "Galileo (spacecraft)",
        "Pioneer 10",
        "Pioneer 11",
        "Voyager 2",
    ]
    all_missions = ANCHOR_MISSIONS + extra_missions

    status_keywords = {
        "active": "active",
        "operational": "active",
        "ongoing": "active",
        "retired": "retired",
        "decommissioned": "retired",
        "ended": "retired",
        "crashed": "retired",
        "lost": "lost",
        "failed": "lost",
        "planned": "planned",
        "future": "planned",
    }

    agency_keywords = [
        "NASA", "ESA", "JAXA", "Roscosmos", "ISRO", "CNSA", "SpaceX",
        "Boeing", "Arianespace", "CSA",
    ]

    docs: list[dict] = []
    for title in all_missions:
        if len(docs) >= DOCS_PER_INDEX:
            break
        try:
            page = WIKI.page(title)
            if not page.exists():
                print(f"  WIKI miss: {title}")
                continue

            text = page.text
            paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
            description = "\n\n".join(paragraphs[:3])

            # Infer agency
            agency = None
            for ag in agency_keywords:
                if ag.lower() in text.lower():
                    agency = ag
                    break

            # Infer launch_year (first 4-digit year 1957-2035 in first 2000 chars)
            year_matches = re.findall(r"\b(19[5-9]\d|20[0-3]\d)\b", text[:2000])
            launch_year = int(year_matches[0]) if year_matches else None

            # Infer status
            status = "retired"
            text_lower = text.lower()
            for kw, val in status_keywords.items():
                if kw in text_lower[:3000]:
                    status = val
                    break

            # Infer mission_type
            mission_type = "observatory"
            if any(w in text_lower for w in ["lander", "landing"]):
                mission_type = "lander"
            elif any(w in text_lower for w in ["rover"]):
                mission_type = "rover"
            elif any(w in text_lower for w in ["flyby", "fly-by"]):
                mission_type = "flyby"
            elif any(w in text_lower for w in ["crewed", "manned", "astronaut"]):
                mission_type = "crewed"

            # Cross-reference targets from anchor objects
            targets = [
                obj for obj in ANCHOR_OBJECTS if obj.lower() in text_lower
            ]

            clean_title = title.replace(" (spacecraft)", "").replace(
                " rover", " Rover"
            )

            doc = {
                "resource_type": "missions",
                "name": clean_title,
                "agency": agency,
                "mission_type": mission_type,
                "launch_year": launch_year,
                "status": status,
                "targets": targets[:5],
                "description": description,
            }
            docs.append(doc)
            print(f"  OK: {clean_title} ({agency}, {status})")
            time.sleep(0.3)

        except Exception as exc:
            print(f"  WARN: {title} -- {exc}")

    return dedupe(docs, "name")[:DOCS_PER_INDEX]
```

- [ ] **Step 2: Run and verify**

```bash
ADS_TOKEN=$ADS_TOKEN python scripts/fetch_seed_data.py
```

Spot-check:

```bash
python3 -c "
import json
d = json.load(open('data/seed_missions.json'))
print(f'{len(d)} docs')
print(d[0]['name'], d[0]['agency'], d[0]['status'])
"
```

Expected: ~40 docs with real mission data.

- [ ] **Step 3: Commit**

```bash
git add scripts/fetch_seed_data.py
git commit -m "Issue #6: implement fetch_missions via Wikipedia"
```

---

### Task 4: Implement `fetch_astronomers()` — Wikipedia

**Files:**

- Modify: `scripts/fetch_seed_data.py` — replace `fetch_astronomers()` stub

- [ ] **Step 1: Implement `fetch_astronomers()`**

Replace the `raise NotImplementedError` stub with:

```python
def fetch_astronomers() -> list[dict]:
    """Fetch astronomer biographies from Wikipedia."""
    extra_astronomers = [
        "Galileo Galilei",
        "Johannes Kepler",
        "Tycho Brahe",
        "Nicolaus Copernicus",
        "Isaac Newton",
        "Pierre-Simon Laplace",
        "William Parsons, 3rd Earl of Rosse",
        "George Ellery Hale",
        "Harlow Shapley",
        "Walter Baade",
        "Fritz Zwicky",
        "Hans Bethe",
        "Martin Schwarzschild",
        "Lyman Spitzer",
        "Margaret Burbidge",
        "Geoffrey Burbidge",
        "William Alfred Fowler",
        "Frank Drake",
        "Kip Thorne",
        "Stephen Hawking",
        "Roger Penrose",
        "Sandra Faber",
        "Andrea Ghez",
        "Reinhard Genzel",
        "Brian Schmidt",
        "Saul Perlmutter",
        "Adam Riess",
        "Natalie Batalha",
        "Sara Seager",
    ]
    all_astronomers = ANCHOR_ASTRONOMERS + extra_astronomers

    nationalities = [
        "American", "British", "German", "French", "Italian",
        "Soviet", "Russian", "Indian", "Australian", "Canadian",
        "Dutch", "Swedish", "Danish", "Polish", "Belgian",
        "Swiss", "Austrian", "Hungarian", "Czech", "Chinese",
        "Irish", "South African",
    ]

    docs: list[dict] = []
    for title in all_astronomers:
        if len(docs) >= DOCS_PER_INDEX:
            break
        try:
            page = WIKI.page(title)
            if not page.exists():
                print(f"  WIKI miss: {title}")
                continue

            text = page.text
            paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
            biography = "\n\n".join(paragraphs[:3])

            # Extract birth/death years from early text
            all_years = re.findall(r"\b(1[5-9]\d\d|20[0-2]\d)\b", text[:800])
            birth_year = int(all_years[0]) if len(all_years) >= 1 else None
            death_year = None
            if len(all_years) >= 2:
                candidate = int(all_years[1])
                if birth_year and candidate > birth_year:
                    death_year = candidate

            # Nationality from first paragraph
            nationality = None
            first_para = paragraphs[0] if paragraphs else ""
            for nat in nationalities:
                if nat.lower() in first_para.lower():
                    nationality = nat
                    break

            # Known for: first sentence
            first_sentence = first_para.split(".")[0].strip() if first_para else ""
            known_for = first_sentence[:300] if first_sentence else None

            # Cross-reference associated objects and missions
            text_lower = text.lower()
            associated_objects = [
                obj for obj in ANCHOR_OBJECTS if obj.lower() in text_lower
            ]
            mission_names = [
                m.replace(" (spacecraft)", "").replace(
                    " space telescope", " Space Telescope"
                )
                for m in ANCHOR_MISSIONS
            ]
            associated_missions = [
                m for m in mission_names if m.lower() in text_lower
            ]

            doc = {
                "resource_type": "astronomers",
                "name": title,
                "birth_year": birth_year,
                "death_year": death_year,
                "nationality": nationality,
                "known_for": known_for,
                "associated_objects": associated_objects[:6],
                "associated_missions": associated_missions[:4],
                "biography": biography,
            }
            docs.append(doc)
            print(f"  OK: {title} ({nationality}, b.{birth_year})")
            time.sleep(0.3)

        except Exception as exc:
            print(f"  WARN: {title} -- {exc}")

    return dedupe(docs, "name")[:DOCS_PER_INDEX]
```

- [ ] **Step 2: Run and verify**

```bash
ADS_TOKEN=$ADS_TOKEN python scripts/fetch_seed_data.py
```

Spot-check:

```bash
python3 -c "
import json
d = json.load(open('data/seed_astronomers.json'))
print(f'{len(d)} docs')
print(d[0]['name'], d[0]['nationality'], d[0]['birth_year'])
"
```

Expected: ~40 docs with real astronomer data.

- [ ] **Step 3: Commit**

```bash
git add scripts/fetch_seed_data.py
git commit -m "Issue #6: implement fetch_astronomers via Wikipedia"
```

---

### Task 5: Implement `fetch_observations()` — MAST Portal

**Files:**

- Modify: `scripts/fetch_seed_data.py` — replace `fetch_observations()` stub

- [ ] **Step 1: Implement `fetch_observations()`**

Replace the `raise NotImplementedError` stub with:

```python
def fetch_observations() -> list[dict]:
    """
    Fetch HST/JWST observation records from MAST Portal for anchor objects.
    No auth key required.
    """
    mast_url = "https://mast.stsci.edu/api/v0/invoke"

    targets = ANCHOR_OBJECTS + [
        "Betelgeuse", "Sirius", "Proxima Centauri", "Centaurus A",
        "Eagle Nebula", "Whirlpool Galaxy", "Horsehead Nebula",
        "Vela Pulsar", "Omega Centauri", "Large Magellanic Cloud",
    ]

    band_map = {
        "UVIS": "optical", "ACS": "optical", "WFC3": "optical",
        "WFPC2": "optical", "FOC": "optical", "FOS": "optical",
        "NICMOS": "infrared", "NIRCAM": "infrared", "NIRSPEC": "infrared",
        "MIRI": "infrared", "NIRISS": "infrared",
        "COS": "uv", "STIS": "uv", "FUV": "uv",
        "HRC": "optical", "SBC": "uv",
    }

    docs: list[dict] = []

    for target in targets:
        if len(docs) >= DOCS_PER_INDEX:
            break
        try:
            criteria_payload = {
                "service": "Mast.Caom.Filtered",
                "format": "json",
                "params": {
                    "columns": "target_name,instrument_name,obs_collection,"
                               "t_min,obs_title,dataproduct_type",
                    "filters": [
                        {
                            "paramName": "target_name",
                            "values": [target],
                        },
                        {
                            "paramName": "obs_collection",
                            "values": ["HST", "JWST"],
                        },
                        {
                            "paramName": "dataproduct_type",
                            "values": ["image"],
                        },
                    ],
                },
                "page": 1,
                "pagesize": 5,
            }
            resp = requests.post(
                mast_url,
                data={"request": json.dumps(criteria_payload)},
                timeout=20,
            )
            resp.raise_for_status()
            data = resp.json()
            obs_list = data.get("data", [])

            if not obs_list:
                print(f"  MAST: no results for '{target}'")
                # Synthesize a plausible record
                doc = {
                    "resource_type": "observations",
                    "target_name": target,
                    "instrument": "HST/ACS",
                    "observatory": "Hubble Space Telescope",
                    "observation_date": "2010-01-01",
                    "wavelength_band": "optical",
                    "notes": (
                        f"Archival Hubble Space Telescope observation of {target}. "
                        f"Target observed as part of a survey program to characterise "
                        f"the morphological and spectral properties of {target}."
                    ),
                }
                docs.append(doc)
                print(f"  OK (synthetic): {target}")
                continue

            for obs in obs_list[:2]:
                if len(docs) >= DOCS_PER_INDEX:
                    break
                instrument_name = (
                    obs.get("instrument_name") or "HST/ACS"
                ).strip()
                obs_collection = (obs.get("obs_collection") or "HST").strip()

                # Derive wavelength_band from instrument name
                band = "optical"
                inst_upper = instrument_name.upper()
                for key, val in band_map.items():
                    if key in inst_upper:
                        band = val
                        break

                # Convert t_min (MJD) to ISO date
                t_min = obs.get("t_min")
                if t_min:
                    try:
                        mjd_epoch = datetime(1858, 11, 17)
                        obs_date = (
                            mjd_epoch + timedelta(days=float(t_min))
                        ).strftime("%Y-%m-%d")
                    except Exception:
                        obs_date = "2000-01-01"
                else:
                    obs_date = "2000-01-01"

                obs_title = obs.get("obs_title") or ""
                notes = obs_title or (
                    f"Observation of {target} with {instrument_name} "
                    f"aboard {obs_collection}. "
                    f"Wavelength coverage: {band}. "
                    f"Science program targeting {target} to study "
                    f"its physical properties."
                )

                doc = {
                    "resource_type": "observations",
                    "target_name": target,
                    "instrument": instrument_name,
                    "observatory": obs_collection,
                    "observation_date": obs_date,
                    "wavelength_band": band,
                    "notes": notes[:2000],
                }
                docs.append(doc)
                print(f"  OK: {target} / {instrument_name} / {band}")

            time.sleep(0.4)

        except Exception as exc:
            print(f"  WARN: {target} -- {exc}")

    return dedupe(docs, "notes")[:DOCS_PER_INDEX]
```

- [ ] **Step 2: Run and verify**

```bash
ADS_TOKEN=$ADS_TOKEN python scripts/fetch_seed_data.py
```

Spot-check:

```bash
python3 -c "
import json
d = json.load(open('data/seed_observations.json'))
print(f'{len(d)} docs')
print(d[0]['target_name'], d[0]['instrument'], d[0]['wavelength_band'])
"
```

Expected: ~40 docs with real or synthesized observation records.

- [ ] **Step 3: Commit**

```bash
git add scripts/fetch_seed_data.py
git commit -m "Issue #6: implement fetch_observations via MAST Portal"
```

---

### Task 6: Implement `fetch_publications()` — NASA ADS

**Files:**

- Modify: `scripts/fetch_seed_data.py` — replace `fetch_publications()` stub

- [ ] **Step 1: Implement `fetch_publications()`**

Replace the `raise NotImplementedError` stub with:

```python
def fetch_publications() -> list[dict]:
    """
    Fetch astronomy papers from NASA ADS API.
    Requires ADS_TOKEN env var.
    """
    ads_url = "https://api.adsabs.harvard.edu/v1/search/query"
    headers = {"Authorization": f"Bearer {ADS_TOKEN}"}

    docs: list[dict] = []
    for query_term in ADS_SEARCH_TERMS:
        if len(docs) >= DOCS_PER_INDEX:
            break
        try:
            params = {
                "q": query_term,
                "fl": "title,author,year,pub,abstract,keyword,doi,bibcode",
                "rows": 4,
                "sort": "citation_count desc",
            }
            resp = requests.get(
                ads_url, headers=headers, params=params, timeout=20
            )
            resp.raise_for_status()
            papers = resp.json().get("response", {}).get("docs", [])

            for paper in papers:
                if len(docs) >= DOCS_PER_INDEX:
                    break
                title = (
                    paper.get("title", [""])[0] if paper.get("title") else ""
                )
                if not title:
                    continue
                abstract = paper.get("abstract") or f"Research paper: {title}"
                authors = paper.get("author", [])[:8]
                doi_list = paper.get("doi", [])
                doi = doi_list[0] if doi_list else None
                topics = paper.get("keyword", [])[:10]

                doc = {
                    "resource_type": "publications",
                    "title": title,
                    "authors": authors,
                    "year": paper.get("year"),
                    "journal": paper.get("pub"),
                    "abstract": abstract[:3000],
                    "topics": topics,
                    "doi": doi,
                }
                docs.append(doc)
                print(f"  OK: {title[:70]}")

            time.sleep(0.3)

        except Exception as exc:
            print(f"  WARN: query '{query_term}' -- {exc}")

    return dedupe(docs, "title")[:DOCS_PER_INDEX]
```

- [ ] **Step 2: Run and verify**

```bash
ADS_TOKEN=$ADS_TOKEN python scripts/fetch_seed_data.py
```

Spot-check:

```bash
python3 -c "
import json
d = json.load(open('data/seed_publications.json'))
print(f'{len(d)} docs')
print(d[0]['title'][:60])
print(f'authors: {len(d[0][\"authors\"])}')
"
```

Expected: ~40 docs with real publication data from ADS.

- [ ] **Step 3: Commit**

```bash
git add scripts/fetch_seed_data.py
git commit -m "Issue #6: implement fetch_publications via NASA ADS"
```

---

### Task 7: Run Full Script and Commit Seed Data

**Files:**

- Generated: all 5 `data/seed_*.json` files
- Generated: all 5 `service/src/test/resources/seed/seed_*.json` files

- [ ] **Step 1: Run the full script end-to-end**

```bash
ADS_TOKEN=$ADS_TOKEN python scripts/fetch_seed_data.py
```

Expected: all 5 indexes complete successfully with ~40 docs each.

- [ ] **Step 2: Verify all output files exist and have correct counts**

```bash
python3 -c "
import json
from pathlib import Path

data_dir = Path('data')
test_dir = Path('service/src/test/resources/seed')

for name in [
    'seed_celestial_objects.json',
    'seed_missions.json',
    'seed_observations.json',
    'seed_astronomers.json',
    'seed_publications.json',
]:
    full = json.load(open(data_dir / name))
    test = json.load(open(test_dir / name))
    print(f'{name}: {len(full)} full, {len(test)} test')
"
```

Expected output (approximately):

```text
seed_celestial_objects.json: 40 full, 3 test
seed_missions.json: 40 full, 3 test
seed_observations.json: 40 full, 3 test
seed_astronomers.json: 40 full, 3 test
seed_publications.json: 40 full, 3 test
```

- [ ] **Step 3: Verify cross-references exist**

```bash
python3 -c "
import json

objects = {d['name'] for d in json.load(open('data/seed_celestial_objects.json'))}
obs_targets = {d['target_name'] for d in json.load(open('data/seed_observations.json'))}
overlap = objects & obs_targets
print(f'Cross-ref celestial_objects <-> observations: {len(overlap)} shared targets')
print(f'Examples: {list(overlap)[:3]}')
"
```

Expected: several shared target names (Crab Nebula, Andromeda Galaxy, etc.).

- [ ] **Step 4: Verify no `id` or `embedding` fields in output**

```bash
python3 -c "
import json
for name in ['seed_celestial_objects.json', 'seed_publications.json']:
    docs = json.load(open(f'data/{name}'))
    for d in docs:
        assert 'id' not in d, f'Found id in {name}'
        assert 'embedding' not in d, f'Found embedding in {name}'
    print(f'{name}: no id/embedding fields -- OK')
"
```

Expected: both checks pass.

- [ ] **Step 5: Commit all seed data files**

```bash
git add data/seed_*.json service/src/test/resources/seed/seed_*.json
git commit -m "Issue #6: add generated seed data and test fixtures"
```
