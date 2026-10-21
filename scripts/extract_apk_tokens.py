"""Extract the official Android app's design tokens from a vendor APK.

The official app is a uni-app bundle: the native shell is closed source, so the
compiled CSS inside the APK is the only authoritative spec for its look. This
script pulls the `--ink-*` palette out of `assets/apps/__UNI__*/www/app.css` and
writes the same JSON shape that `docs/reference/apk-*/theme-tokens-*.json` uses,
so a future release can be diffed in one command.

Usage:
    python3 scripts/extract_apk_tokens.py path/to/EkkoStudio.apk out_dir

Then compare:
    diff -u docs/reference/apk-1.0.8/theme-tokens-dark.json out_dir/theme-tokens-dark.json

Verified on 1.0.3 / 1.0.4 / 1.0.8: the palette is identical across all three.
"""
import json
import os
import re
import sys
import zipfile


def read_css(apk_path: str) -> str:
    with zipfile.ZipFile(apk_path) as archive:
        candidates = [
            name for name in archive.namelist()
            if name.startswith("assets/apps/") and name.endswith("/www/app.css")
        ]
        if not candidates:
            raise SystemExit("no assets/apps/*/www/app.css inside the APK")
        # the richest file wins if a bundle ever ships more than one app
        best = max(candidates, key=lambda n: archive.getinfo(n).file_size)
        return archive.read(best).decode("utf-8", "ignore")


def block_with_tokens(css: str, predicate):
    for match in re.finditer(r"([^{}]{0,120})\{([^{}]*--ink-[^{}]*)\}", css):
        selector = " ".join(match.group(1).split())
        if predicate(selector):
            return dict(re.findall(r"(--ink-[a-z0-9-]+)\s*:\s*([^;]+);", match.group(2)))
    return {}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    apk, out_dir = sys.argv[1], sys.argv[2]
    css = read_css(apk)

    light = block_with_tokens(css, lambda s: s == "body")
    dark = block_with_tokens(css, lambda s: s == ".theme-dark")
    if not light or not dark:
        raise SystemExit("token blocks not found (expected `body` and `.theme-dark`)")

    os.makedirs(out_dir, exist_ok=True)
    for name, tokens in (("light", light), ("dark", dark)):
        path = os.path.join(out_dir, f"theme-tokens-{name}.json")
        with open(path, "w") as handle:
            json.dump({k: tokens[k] for k in sorted(tokens)}, handle, indent=2)
            handle.write("\n")
        print(f"{path}: {len(tokens)} tokens")


if __name__ == "__main__":
    main()
