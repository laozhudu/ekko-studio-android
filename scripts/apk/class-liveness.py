#!/usr/bin/env python3
"""Class-liveness check for parity documents.

A selector appearing in an official stylesheet is **not** evidence that the
shipped app renders it. uni-app compiles both the live template and dead
leftovers of renamed components into the same `pages-index.css`, and the dead
ones look exactly like the live ones. This script answers the other half of the
question: does some component actually *emit* this class?

The check is a **class-token** test, not a substring test. A class counts as
emitted only when it appears as a token in

  * a `class:"..."` / `"class":"..."` attribute value — the bundle is minified,
    so the bare key is unquoted and hyphenated keys are quoted,
  * a `"hover-class":"..."` value,
  * a `"placeholder-class":"..."` value,
  * a string literal (or object key) inside a `normalizeClass([...])` array, or
  * a dynamic form that provably yields it: `"prefix--" + <ternary>` or a
    `` `prefix--${...}` `` template whose branch literals are also in the bundle.

Substring matching is what produces false positives: `new-chat-header` contains
`chat-header`, so a naive `in` test blesses a class nothing emits.

Usage:
    scripts/apk/class-liveness.py <HStudioDirect> <doc.md> [doc2.md ...]
    scripts/apk/class-liveness.py --release 1.0.3 <HStudioDirect> docs/parity/*.md

The default release is 1.0.4. `--release both` reports a class that is live in
one release and dead in the other, which is a finding worth seeing.

Exit status is 0 when every cited class is either emitted or named in a
dead-class note nearby in the same document, else 1 — so it can gate a doc pass.

Known limits, so a green run is not read as more than it is:

  * Only **backticked** citations (`` `.some-class` ``) are checked. A name
    written plainly in prose is invisible to the gate — write class names in
    backticks in parity docs.
  * A class assembled from parts this script does not model (an array `.join`,
    a lookup table) reads as dead. When it does, check by hand and record the
    class in a note rather than contorting the pattern.
  * "Acknowledged" is a heuristic over nearby text, not a proof. It says a
    human wrote down why the class is absent; it does not check that the reason
    is true.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# The bundle is minified and uses both key spellings: `{class:"card"}` for the
# bare name, and `{"hover-class":"card--pressed"}` for hyphenated keys, which
# Vue's compiler cannot emit as identifiers (note the quotes end up *around* the
# key: `"hover-class":"…"`, not `hover-class:"…"`). `"?` absorbs the optional
# quote and `(?:^|[^-\w])` keeps a `-class:` suffix from re-matching the tail of
# `hover-class:` / `placeholder-class:`.
CLASS_ATTR = re.compile(r'(?:^|[^-\w])"?class"?:"([^"]{0,600})"')
HOVER_CLASS = re.compile(r'(?:^|[^-\w])"?hover-class"?:"([^"]{0,600})"')
PLACEHOLDER_CLASS = re.compile(r'(?:^|[^-\w])"?placeholder-class"?:"([^"]{0,600})"')
# Every dynamic class call in the bundle is `e.normalizeClass([...])` — 399 sites,
# no other builder — so one alternative is enough.
NORMALIZE_BLOCK = re.compile(r'normalizeClass\(\[(.*?)\]\)', re.S)
STRING_LITERAL = re.compile(r'"([^"]{0,300})"')
CLASS_KEY = re.compile(r'\{?"([a-z][\w-]*)"\s*:')
CONCAT_BASE = re.compile(r'"([a-zA-Z][\w-]*)-"\s*\+')
TEMPLATE_BASE = re.compile(r'`([a-zA-Z][\w-]*-)\$\{')
CITED = re.compile(r'`\.([a-z][a-z0-9-]*)`')

# A dead class is "acknowledged" when the document says so in the same breath.
# Two legitimate categories: dead CSS, and a class cited only as the desktop/web
# counterpart this port deliberately does not carry.
DEAD_MARKERS = ("dead", "not exist", "0 times", "0 occurrences", "0 / 0",
                "0 components", "never emitted", "no component", "absent",
                "not been compared", "not a parity claim", "not implemented",
                "desktop", "unreplicated")
ACK_WINDOW = 4  # a name and its verdict routinely sit several lines apart

BUNDLE = "latest/decoded-v{}/assets/apps/__UNI__1F41684/www/app-service.js"
RELEASES = {"1.0.3": "103", "1.0.4": "104"}


class Tokens:
    """The class vocabulary one bundle can emit.

    `names` are whole class tokens proven to reach an element. `literals` are
    every quoted string in the bundle, used only to resolve the branches of a
    dynamically built class (`"prefix-" + (x ? "a" : "b")`), where the branch
    literal lives in a helper function and need not be a class token itself.
    """

    def __init__(self, js: str) -> None:
        self.names: set[str] = set()
        self.stems: set[str] = set()
        self.literals: set[str] = {s for s in STRING_LITERAL.findall(js) if s}

        def add(value: str) -> None:
            self.names.update(value.split())

        for pat in (CLASS_ATTR, HOVER_CLASS, PLACEHOLDER_CLASS):
            for m in pat.finditer(js):
                add(m.group(1))

        # normalizeClass([...]) — array literals and the keys of its object form,
        # both of which are class names by construction.
        for m in NORMALIZE_BLOCK.finditer(js):
            body = m.group(1)
            for lit in STRING_LITERAL.findall(body):
                add(lit)
            for key in CLASS_KEY.findall(body):
                self.names.add(key)

        for m in CONCAT_BASE.finditer(js):
            self.stems.add(m.group(1) + "-")
        for m in TEMPLATE_BASE.finditer(js):
            self.stems.add(m.group(1))

    def emits(self, name: str) -> bool:
        if name in self.names:
            return True
        # A dynamic site only counts when the stem is a BEM modifier (`…--`), so
        # the suffix is one of a closed set of variants. A bare `word-` prefix
        # joined to an arbitrary literal is not evidence: the `"chat-" + …` site
        # in the bundle would otherwise "prove" the phantom `.chat-header` simply
        # because `"header"` is some other string in the 3.5 MB file.
        return any(
            name[len(stem):] in self.literals
            for stem in self.stems
            if "--" in stem and name.startswith(stem)
        )


def acknowledged(lines: list[str], name: str) -> bool:
    """Is this class named in a note that explains why it is absent?"""
    for i, ln in enumerate(lines):
        if name not in ln:
            continue
        window = lines[max(0, i - ACK_WINDOW):i + ACK_WINDOW + 1]
        if any(any(k in w for k in DEAD_MARKERS) for w in window):
            return True
    return False


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("root", help="HStudioDirect dir holding latest/decoded-v10{3,4}")
    ap.add_argument("docs", nargs="+", help="parity documents to check")
    ap.add_argument("--release", default="1.0.4", choices=[*RELEASES, "both"],
                    help="which bundle(s) to load (default 1.0.4)")
    args = ap.parse_args()

    wanted = list(RELEASES) if args.release == "both" else [args.release]
    tokens: dict[str, Tokens] = {}
    for rel in wanted:
        path = Path(args.root) / BUNDLE.format(RELEASES[rel])
        if not path.exists():
            print(f"missing bundle: {path}", file=sys.stderr)
            return 2
        tokens[rel] = Tokens(path.read_text(encoding="utf-8", errors="replace"))

    dead = missing = 0
    for doc in args.docs:
        d = Path(doc)
        if not d.exists():
            print(f"missing doc: {d}", file=sys.stderr)
            missing += 1
            continue
        lines = d.read_text(encoding="utf-8").split("\n")
        names = sorted({n for n in CITED.findall("\n".join(lines)) if n != "vue"})
        print(f"\n== {doc} — {len(names)} class names cited")

        for name in names:
            lives = {rel: tokens[rel].emits(name) for rel in wanted}
            if any(lives.values()):
                if not all(lives.values()):
                    live_rel = ",".join(r for r, v in lives.items() if v)
                    dead_rel = ",".join(r for r, v in lives.items() if not v)
                    print(f"   🔶 .{name:40s} live in {live_rel}, NOT {dead_rel}")
                continue
            if acknowledged(lines, name):
                print(f"   ✔ .{name:40s} dead, acknowledged")
            else:
                dead += 1
                print(f"   ✖ .{name:40s} DEAD and not acknowledged")

    print()
    if dead or missing:
        if dead:
            print(f"{dead} unacknowledged dead class(es): either fix the row or "
                  f"record the class in a dead-class note.")
        if missing:
            print(f"{missing} document(s) not found.")
        return 1
    print("Every cited class is emitted by some component, or is already "
          "recorded as dead.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
