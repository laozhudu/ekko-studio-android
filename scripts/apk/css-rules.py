#!/usr/bin/env python3
"""Query rules out of the compiled CSS extracted from the official HStudio APK.

The official APK is a uni-app bundle whose stylesheets are the only ground truth
for the native shell pages (login/devices/about/bootstrap), so parity work needs
a way to ask "what does the official stylesheet actually say about .foo".

Usage:
    css-rules.py <file.css> <selector-substring> [--props a,b,c] [--context N]

Examples:
    scripts/apk/css-rules.py docs/reference/apk-1.0.3/css/pages-login.css login-button
    scripts/apk/css-rules.py docs/reference/apk-1.0.3/css/app.css --props --ink-text-primary
"""

from __future__ import annotations

import argparse
import re
import sys

RULE_RE = re.compile(r"([^{}]+)\{([^{}]*)\}")


def iter_rules(css: str):
    """Yield (selector, body) for every flat rule, skipping at-rule preludes."""
    for m in RULE_RE.finditer(css):
        selector = m.group(1).strip()
        if not selector or selector.startswith("@"):
            continue
        # Keyframe steps read as "0%"/"to" between @keyframes braces; drop them.
        if re.fullmatch(r"(\d+%|from|to)(\s*,\s*(\d+%|from|to))*", selector):
            continue
        yield selector, m.group(2).strip()


def declarations(body: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for part in body.split(";"):
        if ":" not in part:
            continue
        key, _, value = part.partition(":")
        out[key.strip()] = value.strip()
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("css_file")
    ap.add_argument("needle", nargs="?", default="", help="substring matched against the selector")
    ap.add_argument("--props", help="comma-separated property allowlist")
    ap.add_argument("--context", type=int, default=0, help="also match rules whose selector contains these N chars of needle")
    args = ap.parse_args()

    css = open(args.css_file, encoding="utf-8", errors="replace").read()
    allow = set(args.props.split(",")) if args.props else None
    hits = 0
    for selector, body in iter_rules(css):
        # uni-app scopes every selector with [data-v-<hash>]; strip it for readability.
        readable = re.sub(r"\[data-v-[0-9a-f]+\]", "", selector)
        if args.needle and args.needle not in readable:
            continue
        decls = declarations(body)
        if allow:
            decls = {k: v for k, v in decls.items() if k in allow}
        if not decls:
            continue
        hits += 1
        print(f"{readable} {{")
        for k, v in decls.items():
            print(f"    {k}: {v};")
        print("}")
    print(f"\n# {hits} rule(s) matched in {args.css_file}", file=sys.stderr)
    return 0 if hits else 1


if __name__ == "__main__":
    raise SystemExit(main())
