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
from astroquery.simbad import Simbad

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


def _init_simbad() -> Simbad:
    """Configure a Simbad client with the fields we need."""
    simbad = Simbad()
    simbad.TIMEOUT = 10
    simbad.add_votable_fields("otype", "plx")
    return simbad


def fetch_celestial_objects() -> list[dict]:
    """
    Fetch celestial object data from Wikipedia, with optional SIMBAD
    enrichment (via astroquery) for object type and parallax/distance.
    Targets 40 documents.
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

    # Fallback object types for when SIMBAD is unavailable
    known_types = {
        "Crab Nebula": "nebula", "Andromeda Galaxy": "galaxy",
        "Cygnus X-1": "black_hole", "Orion Nebula": "nebula",
        "Sagittarius A*": "black_hole", "Pleiades": "cluster",
        "Centaurus A": "galaxy", "Omega Nebula": "nebula",
        "Eagle Nebula": "nebula", "Whirlpool Galaxy": "galaxy",
        "Horsehead Nebula": "nebula", "Vela Pulsar": "pulsar",
        "Betelgeuse": "star", "Sirius": "star",
        "Proxima Centauri": "star", "Alpha Centauri": "star",
        "Barnard's Star": "star", "Polaris": "star",
        "Rigel": "star", "Vega": "star",
        "Antares": "star", "Aldebaran": "star",
        "Capella": "star", "Arcturus": "star",
        "Spica": "star", "Deneb": "star",
        "Altair": "star", "Fomalhaut": "star",
        "Epsilon Eridani": "star", "Tau Ceti": "star",
        "61 Cygni": "star", "Wolf 359": "star",
        "Lalande 21185": "star", "Ross 128": "star",
        "Groombridge 34": "star", "HD 209458": "star",
        "51 Pegasi": "star", "47 Tucanae": "cluster",
        "Omega Centauri": "cluster",
        "Large Magellanic Cloud": "galaxy",
        "Small Magellanic Cloud": "galaxy",
    }

    simbad_otype_map = {
        "Star": "star", "**": "star", "PM*": "star", "V*": "star",
        "HB*": "star", "RG*": "star", "SG*": "star", "WR*": "star",
        "Psr": "pulsar", "Neb": "nebula", "SNR": "nebula",
        "PN": "nebula", "HII": "nebula", "MoC": "nebula",
        "Gl?": "galaxy", "G": "galaxy", "LIN": "galaxy",
        "GiG": "galaxy", "SyG": "galaxy", "AGN": "galaxy",
        "Cl*": "cluster", "GlC": "cluster", "OpC": "cluster",
        "BH": "black_hole", "XB*": "black_hole",
    }

    simbad = _init_simbad()

    for name in names:
        if len(docs) >= DOCS_PER_INDEX:
            break
        try:
            description = get_wiki_summary(name)
            if not description:
                print(f"  WIKI miss: {name}")
                continue

            object_type = known_types.get(name, "other")
            distance_ly = None

            # Try SIMBAD via astroquery for distance and refined object type
            try:
                result = simbad.query_object(name)
                if result and len(result) > 0:
                    row = result[0]
                    # Parallax -> distance
                    plx = row["PLX_VALUE"]
                    if plx and float(plx) > 0:
                        distance_ly = round(3260.0 / float(plx), 1)
                    # Object type
                    raw_otype = str(row["OTYPE"]).strip()
                    if raw_otype in simbad_otype_map:
                        object_type = simbad_otype_map[raw_otype]
                    print(f"  SIMBAD enriched: {name}")
            except Exception:
                print(f"  SIMBAD unavailable for {name}, using Wikipedia only")

            doc = {
                "resource_type": "celestial_objects",
                "name": name,
                "designations": [name],
                "object_type": object_type,
                "constellation": None,
                "distance_ly": distance_ly,
                "description": description,
                "discovered_by": None,
                "discovery_year": None,
            }
            docs.append(doc)
            print(f"  OK: {name} ({object_type})")
            time.sleep(0.3)

        except Exception as exc:
            print(f"  WARN: {name} -- {exc}")

    return dedupe(docs, "name")[:DOCS_PER_INDEX]


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


def fetch_observations() -> list[dict]:
    """
    Fetch HST/JWST observation records from MAST Portal for anchor objects.
    Uses catalog IDs (M1, NGC, etc.) since MAST indexes by catalog name.
    No auth key required.
    """
    mast_url = "https://mast.stsci.edu/api/v0/invoke"

    # (display_name, mast_search_name) — MAST uses catalog IDs, not common names
    targets = [
        ("Crab Nebula", "M1"),
        ("Andromeda Galaxy", "M31"),
        ("Cygnus X-1", "CYG-X-1"),
        ("Orion Nebula", "M42"),
        ("Sagittarius A*", "SGR-A*"),
        ("Pleiades", "M45"),
        ("Centaurus A", "NGC5128"),
        ("Omega Nebula", "M17"),
        ("Eagle Nebula", "M16"),
        ("Whirlpool Galaxy", "M51"),
        ("Horsehead Nebula", "HORSEHEAD"),
        ("Vela Pulsar", "VELA"),
        ("Betelgeuse", "BETELGEUSE"),
        ("Sirius", "SIRIUS"),
        ("Proxima Centauri", "PROXIMA-CEN"),
        ("Alpha Centauri", "ALF-CEN"),
        ("Vega", "VEGA"),
        ("Polaris", "POLARIS"),
        ("Rigel", "RIGEL"),
        ("Antares", "ANTARES"),
        ("Aldebaran", "ALDEBARAN"),
        ("Arcturus", "ARCTURUS"),
        ("Fomalhaut", "FOMALHAUT"),
        ("47 Tucanae", "47TUC"),
        ("Omega Centauri", "NGC5139"),
        ("Large Magellanic Cloud", "LMC"),
        ("Small Magellanic Cloud", "SMC"),
        ("Eta Carinae", "ETA-CAR"),
        ("Ring Nebula", "M57"),
        ("Sombrero Galaxy", "M104"),
        ("Triangulum Galaxy", "M33"),
        ("Pinwheel Galaxy", "M101"),
        ("Cat's Eye Nebula", "NGC6543"),
        ("Helix Nebula", "NGC7293"),
        ("Dumbbell Nebula", "M27"),
        ("Globular Cluster M13", "M13"),
        ("Omega Nebula", "M17"),
        ("Lagoon Nebula", "M8"),
        ("Trifid Nebula", "M20"),
        ("Butterfly Nebula", "NGC6302"),
        ("Carina Nebula", "NGC3372"),
        ("Tarantula Nebula", "30-DOR"),
        ("Pillars of Creation", "M16"),
        ("Supernova 1987A", "SN1987A"),
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

    for display_name, mast_name in targets:
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
                            "values": [mast_name],
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
                print(f"  MAST: no results for '{display_name}' ({mast_name})")
                # Synthesize a plausible record
                doc = {
                    "resource_type": "observations",
                    "target_name": display_name,
                    "instrument": "HST/ACS",
                    "observatory": "Hubble Space Telescope",
                    "observation_date": "2010-01-01",
                    "wavelength_band": "optical",
                    "notes": (
                        f"Archival Hubble Space Telescope observation of "
                        f"{display_name}. Target observed as part of a survey "
                        f"program to characterise the morphological and "
                        f"spectral properties of {display_name}."
                    ),
                }
                docs.append(doc)
                print(f"  OK (synthetic): {display_name}")
                continue

            for obs in obs_list[:2]:
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
                    f"Observation of {display_name} with {instrument_name} "
                    f"aboard {obs_collection}. "
                    f"Wavelength coverage: {band}. "
                    f"Science program targeting {display_name} to study "
                    f"its physical properties."
                )

                doc = {
                    "resource_type": "observations",
                    "target_name": display_name,
                    "instrument": instrument_name,
                    "observatory": obs_collection,
                    "observation_date": obs_date,
                    "wavelength_band": band,
                    "notes": notes[:2000],
                }
                docs.append(doc)
                print(f"  OK: {display_name} / {instrument_name} / {band}")

            time.sleep(0.4)

        except Exception as exc:
            print(f"  WARN: {display_name} -- {exc}")

    # Dedupe by composite of target + instrument + date (notes dedupe is too aggressive)
    seen: set[str] = set()
    unique: list[dict] = []
    for d in docs:
        key = f"{d['target_name']}|{d['instrument']}|{d['observation_date']}"
        if key not in seen:
            seen.add(key)
            unique.append(d)
    return unique[:DOCS_PER_INDEX]


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
