#!/usr/bin/env python3
"""Check native-library coverage in the built Google Play bundle."""

from pathlib import Path
import unittest
from zipfile import ZipFile


BUNDLE = (
    Path(__file__).resolve().parents[1]
    / "app/build/outputs/bundle/googleRelease/app-google-release.aab"
)


class GooglePlayBundleTest(unittest.TestCase):
    def test_every_packaged_abi_has_map_libraries(self):
        libraries = {}
        with ZipFile(BUNDLE) as bundle:
            for name in bundle.namelist():
                parts = name.split("/")
                if len(parts) == 4 and parts[:2] == ["base", "lib"] and name.endswith(".so"):
                    libraries.setdefault(parts[2], set()).add(parts[3])

        self.assertTrue({"arm64-v8a", "armeabi-v7a", "x86_64"} <= libraries.keys())
        required = {"libjniMaplibreNativeC.so", "libmaplibre-native-c.so"}
        for abi, present in libraries.items():
            self.assertFalse(required - present, f"{abi} is missing {required - present}")


if __name__ == "__main__":
    unittest.main()
