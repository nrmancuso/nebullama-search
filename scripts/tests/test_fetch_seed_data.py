import importlib.util
import unittest
from pathlib import Path


def load_fetch_seed_data_module():
    module_path = Path(__file__).resolve().parents[1] / "fetch_seed_data.py"
    spec = importlib.util.spec_from_file_location("fetch_seed_data", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class FetchSeedDataTest(unittest.TestCase):
    def test_extract_simbad_enrichment_reads_lowercase_columns(self):
        module = load_fetch_seed_data_module()

        object_type, distance_ly = module.extract_simbad_enrichment(
            {"otype": "**", "plx_value": 379.21}, {"**": "star"}, "other"
        )

        self.assertEqual("star", object_type)
        self.assertEqual(8.6, distance_ly)

    def test_extract_simbad_enrichment_supports_legacy_uppercase_columns(self):
        module = load_fetch_seed_data_module()

        object_type, distance_ly = module.extract_simbad_enrichment(
            {"OTYPE": "Psr", "PLX_VALUE": 10.0}, {"Psr": "pulsar"}, "other"
        )

        self.assertEqual("pulsar", object_type)
        self.assertEqual(326.0, distance_ly)

    def test_merge_existing_embeddings_preserves_embedding_for_matching_doc(self):
        module = load_fetch_seed_data_module()

        merged = module.merge_existing_embeddings(
            [{"name": "Sirius", "distance_ly": 8.6}],
            [{"name": "Sirius", "distance_ly": None, "embedding": [0.1, 0.2]}],
        )

        self.assertEqual([0.1, 0.2], merged[0]["embedding"])
        self.assertEqual(8.6, merged[0]["distance_ly"])


if __name__ == "__main__":
    unittest.main()
