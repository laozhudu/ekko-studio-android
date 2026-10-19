# Official HStudio APK reference (v1.0.3)

Ground-truth assets extracted from the **official, vendor-signed** HStudio Android
app. Everything under this directory is evidence for the parity work in
`docs/OFFICIAL_PARITY_MATRIX.md`; it is read-only reference material, never
compiled into the app.

## Baseline provenance

The official app is a **uni-app** bundle. Its native shell pages are closed
source, so the compiled CSS/JS inside the APK is the *only* authoritative
specification for them. Do not substitute the community Compose client or a
rebuilt APK for it.

| | |
|---|---|
| File | `HStudioDirect/phase1-work/HStudio.apk` |
| SHA-256 | `bf069da21eabd3dcf0db3015c5b73db208cb19248d1e0aaeebc375211dd20d62` |
| Package | `com.ekkostudio.ai` |
| versionName / versionCode | `1.0.3` / `120` |
| App label | `Ekko Studio` |
| Signer | `C=CN, ST=Shanghai, O=EKKO Learn AI, OU=Mobile, CN=Hermes Studio` |
| uni-app id | `__UNI__1F41684` |
| Embedded web client | vendor build; declares **min Studio 0.7.13** — see below |

> **Superseded.** v1.0.4 is now the parity baseline — see
> `docs/reference/apk-1.0.4/README.md`. The native shell pages are unchanged
> between the two releases apart from one disabled-state rule on login and two
> new cloud-account pages, so the A1/A2/A3 conclusions taken from this extraction
> still stand.

### Why 1.0.3 and not 1.0.1

Two pristine vendor-signed builds exist on this machine. Both carry the same
signing certificate and neither contains any direct-connect patch marker
(`HERMES_APP_ENTITLEMENT`, `skipCloudLogin`, `lanDirect`, …), so both are genuine
official releases rather than earlier rebuilds:

| Build | SHA-256 | versionCode | Label |
|---|---|---|---|
| `HStudio.apk` | `e6308c6ac42098f1b308b6cff99133dbb2f1729405a74ff6800be54ab3c52454` | 101 | HStudio |
| `phase1-work/HStudio.apk` | `bf069da21eabd3dcf0db3015c5b73db208cb19248d1e0aaeebc375211dd20d62` | **120** | Ekko Studio |

**1.0.3 is the baseline** because it is the newer official release, and because
the existing Compose implementation was already targeted at it (commit
`0b831b7 Match HStudio 1.0.3 mobile visual system`).

This is *not* the same file as `HStudio直连App/HStudio-Direct-v1.9.1.apk`
(`d1c9fc50…`), which is this project's own patched build and must never be used
as a visual baseline.

## Web client version pin

**Corrected 2026-09-19.** This section previously claimed the APK embeds web
client v0.7.13. It does not. The literal `0.7.13` appears 21 times in
`app-service.js`, and *all 21 are the same thing* — the minimum Studio **server**
version this client will connect to:

| Occurrences | What it is |
|---|---|
| 20 | the locale variants of one i18n message — `"当前 Studio 版本过低，请升级到 0.7.13 或以上版本后再连接。"` and its en/fr/de/es/pt/ru/ar/ja/ko counterparts |
| 1 | a constant — `const Ar="0.7.13"` |

The constant is not a client version either. It is the default argument of the
semver comparator, `function _r(e, t = Ar)`, which `Vr()` uses to reject a server
that is too old:

```js
class xr extends Error { constructor(e){ super("studio_version_unsupported")
  this.name = "UnsupportedStudioVersionError"; this.actualVersion = String(e||"").trim() } }
function Vr(e){ if(!_r(e)) throw new xr(e) }
```

So the string and the constant are two faces of one version gate, and **the
bundle carries no self-identifying client version**. Reading `Ar` as "the bundled
client version" is the exact mistake this section used to make.

The client is a **vendor build**: upstream-derived code plus a private mobile
layer. Of the named components in this bundle, 85 of 87 have no counterpart in
public source. No published tag can serve as its source of truth — including
canonical `EKKOLearnAI/hermes-studio` v1.0.4 (= `ekko-studio` 0.7.23, the newest
available), which contains none of the `App*` mobile components.

`ref-v0.7.13` is still the right checkout for the upstream-derived majority of
the client surface, and it is pinned locally, so it stays useful:

```sh
git -C /root/Hermes任务文件/hermes-studio/v0.7.18 \
    fetch --depth 1 origin refs/tags/v0.7.13:refs/tags/v0.7.13
git -C /root/Hermes任务文件/hermes-studio/v0.7.18 \
    worktree add --detach /root/Hermes任务文件/HStudioDirect/ref-v0.7.13 v0.7.13
```

Use `ref-v0.7.13` — **not** v0.7.18 — when reading Vue source for parity work.
`docs/STUDIO_V0712_PARITY.md` predates this and tracks v0.7.12, so it drifts from
the shipped app in both directions. For the mobile-only surfaces (terminal,
incoming files, paging paddle), the APK's compiled CSS is the only ground truth.

## Contents

| Path | What it is |
|---|---|
| `theme-tokens-light.json` | The 50 `--ink-*` custom properties defined on `body` in `app.css` — the entire light palette |
| `theme-tokens-dark.json` | The 50 `--ink-*` properties defined on `.theme-dark` |
| `css/app.css` | Base stylesheet: theme tokens, Quill + highlight.js vendor styles |
| `css/pages-login.css` | Login page — includes the `@media (max-width:600px)` phone block |
| `css/pages-devices.css` | Device list / add-device page |
| `css/pages-index.css` | The embedded web client's compiled CSS (522 KB) |
| `css/pages-about.css`, `css/pages-bootstrap.css`, `css/pages-location-picker.css` | Remaining uni-app shell pages |
| `icons/` | 80 official SVG/PNG icons from `static/icons` |
| `agents/` | 7 agent-family logos (claude, codex, deepseek, ekko, grok, opencode, pi) |
| `uni-manifest.json` | Decoded `manifest.json` from the bundle |

## Reading the CSS

uni-app scopes every selector with `[data-v-<hash>]`. `scripts/apk/css-rules.py`
strips that and lets you query rules directly:

```sh
scripts/apk/css-rules.py docs/reference/apk-1.0.3/css/pages-login.css field-shell
scripts/apk/css-rules.py docs/reference/apk-1.0.3/css/app.css --props --ink-text-primary
```

### A rule is not a rendered element

`css/pages-index.css` holds every rule the build compiled, including leftovers of
renamed components — `.input-wrapper` and `.input-textarea` have rules here and
**nothing emits them**. Confirm an emitter before citing a selector as the spec:

```sh
scripts/apk/class-liveness.py --release both /root/Hermes任务文件/HStudioDirect \
    docs/parity/*.md docs/OFFICIAL_PARITY_MATRIX.md
```

It tests **class tokens**, not substrings (`new-chat-header` contains
`chat-header`, so `grep` confirms classes that do not exist), resolves dynamically
built names, and flags a class live in one release and dead in the other. See
`docs/reference/apk-1.0.4/README.md` for the full account.

### The `--ink-*` token contract

Colours are **never hard-coded** in the official stylesheets. Every one resolves
through an `--ink-*` property, and most of those are themselves overridable by a
user wallpaper theme (`var(--app-custom-*, <default>)`). A parity implementation
that inlines a hex value instead of routing through the token loses the custom
theme support the official app has — see `Theme.kt` for the Compose mirror.

Note that `--ink-*` values differ by more than colour between themes; e.g.
`--ink-icon-filter` is `none` in light but `invert(1)` in dark, so icons are
recoloured by a filter rather than by swapping assets.

## Reproducing the extraction

The whole directory is regenerated by one scripted pass — decode, then extract:

```sh
# 1. decode the official APK (needs Java 17)
java -jar work/apktool.jar d -f -o latest/decoded-v103 <official.apk>

# 2. extract the reference assets
scripts/apk/extract-official-reference.py \
    latest/decoded-v103 docs/reference/apk-1.0.3
```

The script reads only `assets/apps/__UNI__1F41684/www` and writes only into the
output directory. It is how this directory was validated: re-running it against
`latest/decoded-v103` reproduces all 98 files byte-for-byte apart from the token
JSON, which the earlier hand extraction had mis-labelled.

> **Token-count correction (2026-09-19).** This file previously listed **52**
> light tokens. Two of those keys — `background` (`var(--ink-bg-primary)`) and
> `color` (`var(--ink-text-primary)`) — are ordinary declarations on the `body`
> rule, not `--ink-*` custom properties, and had been swept in by a
> `--ink-*`-prefix-free regex. The real counts are **50 light / 50 dark**; the
> two `theme-tokens-*.json` files here were regenerated and the light file's
> extra keys removed. Dark values were already correct (only trailing-newline
> whitespace changed). Any audit that used the number 52 was comparing against a
> mis-stated denominator.

`app-service.js` (3.3 MB) holds every page's compiled Vue render function; it is
the source for structural questions the CSS cannot answer.
