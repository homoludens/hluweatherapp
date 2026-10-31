#!/usr/bin/env python3
"""Non-visual checks for the static GitHub Pages site."""

from html.parser import HTMLParser
from pathlib import Path
import re
import unittest
from urllib.parse import unquote, urlsplit


SITE = Path(__file__).resolve().parents[1] / "docs"
PAGES = (SITE / "index.html", SITE / "privacy" / "index.html")
THEME = SITE.parent / "app/src/main/java/net/droopia/hluweather/ui/theme"


def css_palette(css, dark=False):
    prefix = r"@media\s*\(prefers-color-scheme:\s*dark\)\s*\{\s*" if dark else ""
    match = re.search(prefix + r":root\s*\{([^}]*)\}", css)
    if match is None:
        raise ValueError("Missing light or dark CSS palette")
    declarations = match.group(1)
    colors = dict(re.findall(r"(--[\w-]+)\s*:\s*(#[0-9a-fA-F]{6})\s*;", declarations))
    return declarations, {name: value.lower() for name, value in colors.items()}


def app_palette(source, name):
    match = re.search(rf"\bval {name}\s*=\s*\w+\((.*?)\n\)", source, re.DOTALL)
    if match is None:
        raise ValueError(f"Missing app palette {name}")
    return {
        name: f"#{value.lower()}"
        for name, value in re.findall(r"(\w+)\s*=\s*Color\(0xFF([0-9a-fA-F]{6})\)", match.group(1))
    }


def contrast_ratio(first, second):
    def luminance(color):
        channels = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
        linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
        return sum(c * weight for c, weight in zip(linear, (0.2126, 0.7152, 0.0722)))

    light, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


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
        self.color_scheme = None
        self.theme_colors = []
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
        if tag == "meta" and attrs.get("name") == "color-scheme":
            self.color_scheme = attrs.get("content")
        if tag == "meta" and attrs.get("name") == "theme-color":
            self.theme_colors.append((attrs.get("media"), attrs.get("content")))
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

    def test_system_theme_uses_app_palettes_on_both_pages(self):
        css = (SITE / "assets/site.css").read_text(encoding="utf-8")
        material_source = (THEME / "Theme.kt").read_text(encoding="utf-8")
        hlu_source = (THEME / "Color.kt").read_text(encoding="utf-8")
        for mode in ("light", "dark"):
            with self.subTest(mode=mode):
                declarations, colors = css_palette(css, dark=mode == "dark")
                material = app_palette(material_source, f"{mode.title()}Colors")
                hlu = app_palette(hlu_source, f"{mode.title()}HluColors")
                self.assertRegex(declarations, rf"color-scheme:\s*{mode}\s*;")
                for css_name, app_color in {
                    "--background": material["background"],
                    "--surface": hlu["weatherCard"],
                    "--surface-variant": material["surfaceVariant"],
                    "--text": material["onBackground"],
                    "--muted": material["onSurfaceVariant"],
                    "--primary": material["primary"],
                    "--on-primary": material["onPrimary"],
                    "--border": material["outlineVariant"],
                    "--accent": hlu["navSelectedText"] if mode == "light" else material["primary"],
                }.items():
                    self.assertEqual(app_color, colors[css_name], css_name)
                for path, page in self.pages.items():
                    with self.subTest(page=path):
                        self.assertEqual("light dark", page.color_scheme)
                        self.assertIn((f"(prefers-color-scheme: {mode})", colors["--background"]), page.theme_colors)

    def test_theme_text_contrast(self):
        css = (SITE / "assets/site.css").read_text(encoding="utf-8")
        for mode in ("light", "dark"):
            _, colors = css_palette(css, dark=mode == "dark")
            pairs = [
                (foreground, background)
                for foreground in ("--text", "--muted", "--accent")
                for background in ("--background", "--surface", "--surface-variant")
            ] + [("--on-primary", "--primary"), ("--on-primary", "--primary-hover")]
            for foreground, background in pairs:
                with self.subTest(mode=mode, foreground=foreground, background=background):
                    self.assertGreaterEqual(contrast_ratio(colors[foreground], colors[background]), 4.5)

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
