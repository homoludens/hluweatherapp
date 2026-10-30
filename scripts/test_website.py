#!/usr/bin/env python3
"""Non-visual checks for the static GitHub Pages site."""

from html.parser import HTMLParser
from pathlib import Path
import unittest
from urllib.parse import unquote, urlsplit


SITE = Path(__file__).resolve().parents[1] / "docs"
PAGES = (SITE / "index.html", SITE / "privacy" / "index.html")


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.ids = []
        self.links = []
        self.images_missing_alt = []
        self.scripts = []
        self.remote_resources = []
        self.headings = []
        self.has_language = False
        self.has_viewport = False
        self.has_title = False
        self.feed(path.read_text(encoding="utf-8"))
        self.close()

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if "id" in attrs:
            self.ids.append(attrs["id"])
        if tag == "html":
            self.has_language = bool(attrs.get("lang"))
        if tag == "meta" and attrs.get("name") == "viewport":
            self.has_viewport = True
        if tag == "title":
            self.has_title = True
        if tag == "h1":
            self.headings.append(tag)
        if tag == "script":
            self.scripts.append(attrs.get("src", "inline"))
        if tag == "img" and "alt" not in attrs:
            self.images_missing_alt.append(attrs.get("src"))
        for attribute in ("href", "src"):
            if attribute not in attrs:
                continue
            url = attrs[attribute]
            parts = urlsplit(url)
            if parts.scheme or parts.netloc:
                if tag != "a":
                    self.remote_resources.append(url)
            else:
                self.links.append(url)


class WebsiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pages = {path: Page(path) for path in PAGES}

    def test_accessible_page_basics_and_static_resources(self):
        self.assertTrue((SITE / ".nojekyll").is_file())
        for path, page in self.pages.items():
            with self.subTest(page=path):
                self.assertTrue(page.has_language)
                self.assertTrue(page.has_viewport)
                self.assertTrue(page.has_title)
                self.assertEqual(1, len(page.headings))
                self.assertEqual(len(page.ids), len(set(page.ids)))
                self.assertEqual([], page.images_missing_alt)
                self.assertEqual([], page.scripts)
                self.assertEqual([], page.remote_resources)

    def test_internal_links_and_fragments(self):
        for path, page in self.pages.items():
            for url in page.links:
                with self.subTest(page=path, url=url):
                    parts = urlsplit(url)
                    target = (path.parent / unquote(parts.path)).resolve() if parts.path else path
                    self.assertTrue(target.is_relative_to(SITE.resolve()))
                    if target.is_dir():
                        target /= "index.html"
                    self.assertTrue(target.is_file(), f"Missing target: {target}")
                    if parts.fragment and target.suffix == ".html":
                        target_page = self.pages.get(target) or Page(target)
                        self.assertIn(unquote(parts.fragment), target_page.ids)

    def test_policy_describes_services_contact_and_controls(self):
        policy = PAGES[1].read_text(encoding="utf-8")
        for text in (
            "HluWeatherApp", "playstore@droopia.net", "Open-Meteo", "MET Norway",
            "Photon", "Nominatim", "OSRM", "OpenFreeMap", "Google Play services",
            "last-known", "backup", "Clear cache", "HTTPS", "GitHub Pages",
        ):
            with self.subTest(text=text):
                self.assertIn(text, policy)


if __name__ == "__main__":
    unittest.main()
