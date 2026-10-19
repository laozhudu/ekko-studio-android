#!/usr/bin/env python3
"""Extract the official HStudio reference assets out of a decoded APK tree.

The official app is a uni-app bundle: its compiled CSS is the only ground truth
for the native shell pages, so each official release gets a `docs/reference/`
directory holding that CSS, the icon set, and the decoded `--ink-*` theme
tokens. This script is what produces those directories, so the extraction is
reproducible instead of ad-hoc.

Usage:
    scripts/apk/extract-official-reference.py <decoded-apk-root> <out-dir>

`<decoded-apk-root>` is an apktool decode (see docs/reference/apk-*/README.md
for the command). Everything is read from
`assets/apps/<uni-app-id>/www`; nothing is written outside `<out-dir>`.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path

UNI_APP_ID = "__UNI__1F41684"
RULE_RE = re.compile(r"([^{}]+)\{([^{}]*)\}")
INK_RE = re.compile(r"(--ink-[a-z0-9-]+)\s*:\s*([^;]+)")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def ink_tokens(css: str, selector: str) -> dict[str, str]:
    """Return the `--ink-*` custom properties declared on exactly `selector`.

    Only real custom properties are collected: the official stylesheets also set
    plain `background`/`color` on the same rule, and those are not theme tokens.
    """
    out: dict[str, str] = {}
    for match in RULE_RE.finditer(css):
        if match.group(1).strip() != selector:
            continue
        out.update({k: v.strip() for k, v in INK_RE.findall(match.group(2))})
    return out


def copy_css(www: Path, out: Path) -> list[str]:
    """Flatten `app.css` + `pages/<page>/index.css` into `<out>/css`."""
    css_dir = out / "css"
    css_dir.mkdir(parents=True, exist_ok=True)
    written = []

    base = www / "app.css"
    shutil.copyfile(base, css_dir / "app.css")
    written.append("css/app.css")

    for page_css in sorted((www / "pages").glob("*/index.css")):
        name = f"pages-{page_css.parent.name}.css"
        shutil.copyfile(page_css, css_dir / name)
        written.append(f"css/{name}")

    return written


def copy_tree(src: Path, dst: Path, label: str) -> list[str]:
    if not src.is_dir():
        return []
    dst.mkdir(parents=True, exist_ok=True)
    for item in sorted(src.iterdir()):
        if item.is_file():
            shutil.copyfile(item, dst / item.name)
    return [f"{label}/{p.name}" for p in sorted(dst.iterdir()) if p.is_file()]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("decoded_root", type=Path, help="apktool decode of the official APK")
    ap.add_argument("out_dir", type=Path, help="destination, e.g. docs/reference/apk-1.0.4")
    args = ap.parse_args()

    www = args.decoded_root / "assets" / "apps" / UNI_APP_ID / "www"
    if not www.is_dir():
        print(f"not a decoded uni-app bundle: {www}", file=sys.stderr)
        return 1

    out = args.out_dir
    out.mkdir(parents=True, exist_ok=True)

    written = copy_css(www, out)
    written += copy_tree(www / "static" / "icons", out / "icons", "icons")
    written += copy_tree(www / "static" / "agents", out / "agents", "agents")

    app_css = read(www / "app.css")
    light = ink_tokens(app_css, "body")
    dark = ink_tokens(app_css, ".theme-dark")
    for name, tokens in (("theme-tokens-light.json", light), ("theme-tokens-dark.json", dark)):
        (out / name).write_text(
            json.dumps(tokens, indent=1, ensure_ascii=False, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        written.append(name)

    shutil.copyfile(www / "manifest.json", out / "uni-manifest.json")
    written.append("uni-manifest.json")

    print(f"{len(written)} files -> {out}")
    print(f"  light tokens {len(light)}, dark tokens {len(dark)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
