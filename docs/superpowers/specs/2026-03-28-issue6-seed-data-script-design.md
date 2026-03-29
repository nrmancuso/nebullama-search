# Issue #6: Seed Data Fetching Script — Design Spec

## Goal

Automated Python script that fetches ~200 real astronomy documents from public
APIs and writes JSON seed files for five indexes. Both a full dataset (~40 docs
per index) and a small test fixture set (3 docs per index) are produced and
checked into git.

## Architecture

Single-file script (`scripts/fetch_seed_data.py`) using Approach B: one
well-organized file with shared helpers at the top, one fetcher function per
index, and a `main()` orchestrator. Dependencies listed in
`scripts/requirements.txt`.

### Invocation

```sh
ADS_TOKEN=<token> python scripts/fetch_seed_data.py
```

`ADS_TOKEN` is required. The script exits with an error if it is not set.

## Output

### Full dataset — `data/`

Five JSON files, one per index, each a pretty-printed JSON array (~40 objects):

- `data/seed_celestial_objects.json`
- `data/seed_missions.json`
- `data/seed_observations.json`
- `data/seed_astronomers.json`
- `data/seed_publications.json`

Checked into git (remove existing `data/seed_*.json` line from `.gitignore`).

### Test fixtures — `service/src/test/resources/seed/`

Same five filenames, same format. First 3 documents sliced from each full set.
Provides stable, real-data fixtures for integration tests.

### Document format

- Every document includes `resource_type` matching its index name
- No `id` field (generated at ingest time by the Java service)
- No `embedding` field (populated at ingest time by OllamaEmbeddingService)
- Pretty-printed JSON with 2-space indent

## Script Structure

```
Constants & config
  ANCHOR_OBJECTS      (12 items)
  ANCHOR_MISSIONS     (12 items)
  ANCHOR_ASTRONOMERS  (12 items)
  ADS_SEARCH_TERMS    (12 astronomy topic queries)
  DATA_DIR            Path("data/")
  TEST_SEED_DIR       Path("service/src/test/resources/seed/")
  DOCS_PER_INDEX      40
  TEST_DOCS_PER_INDEX 3

Shared helpers
  get_wiki_summary(title) -> str       First ~3 paragraphs from Wikipedia
  get_wiki_page(title) -> str          Full page text for inference
  dedupe(docs, key) -> list[dict]      Remove duplicates by field
  save(directory, filename, docs)      Write JSON array to file

Fetchers
  fetch_celestial_objects() -> list[dict]
  fetch_missions()          -> list[dict]
  fetch_observations()      -> list[dict]
  fetch_astronomers()       -> list[dict]
  fetch_publications()      -> list[dict]

main()
  Validate ADS_TOKEN
  Call each fetcher, print progress
  Write full sets to data/
  Slice first 3 docs, write to test resources
```

## Data Sources & Fetcher Logic

### celestial_objects — SIMBAD TAP + Wikipedia

- Query SIMBAD TAP/ADQL endpoint
  (`https://simbad.cds.unistra.fr/simbad/sim-tap/sync`) for each anchor object
- Extract: `main_id`, `otype`, `plx_value` (parallax)
- Convert parallax to distance: `distance_ly = 3260.0 / plx_value` (null if no
  parallax data)
- Map SIMBAD `otype` codes to standardized values: star, pulsar, nebula, galaxy,
  cluster, black_hole, other
- Fetch Wikipedia summary for `description`
- Expand beyond 12 anchors by querying SIMBAD for related objects in the same
  constellations or types to reach 40 docs

**Fields:** `resource_type`, `name`, `designations` (array), `object_type`,
`constellation`, `distance_ly`, `description`, `discovered_by`, `discovery_year`

### missions — Wikipedia

- Start with 12 anchor missions, expand to 40 with additional well-known
  missions
- Fetch Wikipedia page: first 3 paragraphs as `description`
- Infer from text: `agency` (keyword match: NASA/ESA/JAXA/etc.),
  `launch_year` (4-digit year pattern), `status`
  (active/retired/lost/planned), `mission_type`
  (observatory/lander/rover/flyby/crewed)
- Cross-reference `targets` by scanning text for anchor object names

**Fields:** `resource_type`, `name`, `agency`, `mission_type`, `launch_year`,
`status`, `targets` (array), `description`

### observations — MAST Portal API

- Query MAST (`https://mast.stsci.edu/api/v0/invoke`) for each anchor object
- Extract: `instrument`, `obs_collection` as `observatory`, convert `t_min`
  (MJD) to ISO date
- Infer `wavelength_band` from instrument name: ACS/WFC3 as optical,
  NICMOS/NIRCAM as infrared, COS/STIS as uv
- Synthesize `notes` from observation metadata

**Fields:** `resource_type`, `target_name`, `instrument`, `observatory`,
`observation_date`, `wavelength_band`, `notes`

### astronomers — Wikipedia

- Start with 12 anchors, expand to 40 with additional notable astronomers
- Fetch Wikipedia: first 3 paragraphs as `biography`
- Infer: `birth_year`/`death_year` (year patterns in first 500 chars),
  `nationality` (demonym in first paragraph)
- `known_for`: first sentence of biography
- Cross-reference `associated_objects` and `associated_missions` by matching
  anchor names in text

**Fields:** `resource_type`, `name`, `birth_year`, `death_year`, `nationality`,
`known_for`, `associated_objects` (array), `associated_missions` (array),
`biography`

### publications — NASA ADS API

- Query `https://api.adsabs.harvard.edu/v1/search/query` with 12 search terms,
  ~4 results per term
- `ADS_TOKEN` required (no fallback)
- Extract: `title`, `author` as `authors` (up to 8), `year`, `pub` as
  `journal`, `abstract`, `keyword` as `topics` (up to 10), `doi`

**Fields:** `resource_type`, `title`, `authors` (array), `year`, `journal`,
`abstract`, `topics` (array), `doi`

## Cross-Referencing

Anchor subjects (Crab Nebula, Hubble Space Telescope, Jocelyn Bell Burnell,
etc.) appear across multiple indexes naturally through the shared anchor list:

- celestial_objects: by `name`
- observations: by `target_name`
- missions: by `targets` array
- astronomers: by `associated_objects` and `associated_missions`
- publications: by topic/abstract content

## Error Handling

- Fail early if `ADS_TOKEN` is not set
- Per-document resilience: if a single API call fails (e.g., Wikipedia page not
  found), log a warning and skip that document
- Print progress to stdout as each index completes

## Dependencies

`scripts/requirements.txt`:

```
requests
wikipedia-api
```

## Repo Changes

- Remove `data/seed_*.json` from `.gitignore`
- New: `scripts/fetch_seed_data.py`
- New: `scripts/requirements.txt`
- New: `data/seed_*.json` (5 files, checked in)
- New: `service/src/test/resources/seed/seed_*.json` (5 files, checked in)
