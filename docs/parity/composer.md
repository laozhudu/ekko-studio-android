# Parity record — Chat composer (`AppChatComposer`)

**Ground truth**: `docs/reference/apk-1.0.4/css/pages-index.css` — the compiled
web client CSS *as shipped inside the official APK*, so it is version-exact by
construction. (Also `docs/reference/apk-1.0.3/css/pages-index.css`; the `composer-*`
family is semantically identical in both — see *Release stability* below.)
**Compose target**: `MainActivity.kt:2474` (`Composer`), `:2824`
(`ComposerActionButton`).
**Method**: static stylesheet comparison. **No device screenshot.**

## ✅ Resolved: which class family the shipped composer actually uses

**Corrected 2026-09-19 — the earlier version of this file had this backwards.**

This file previously asserted that the shipped client renders the `input-*` family
and that the `composer-*` family was "a red herring from another component or a
dead code path" which must not be ported. The opposite is true.

The shipped mobile composer is the component **`AppChatComposer`**, and it emits
`composer-*`:

```js
// app-service.js
"composer-status-bar" … e.createElementVNode("view",{class:"composer-wrapper"},[ …
    e.createElementVNode("textarea",{class:"composer-textarea", …})
… ])}}),[["__scopeId","data-v-a139dd89"]]
```

Evidence, all four checks from the v1.0.4 bundle:

| Check | Result |
|---|---|
| `__name` owning scope `data-v-a139dd89` | `AppChatComposer` |
| `"input-wrapper"` anywhere in `app-service.js` (3.5 MB) | **absent** |
| Dynamic construction (`"input-"+x`, `` `input-${x}` ``) | **none** — the class is never built at runtime either |
| Classes `AppSingleChatView` actually emits | only `input-context-*`, `input-status-left`, `context-length-input*` — its own context meter |

So `.input-wrapper`, `.input-textarea`, `.input-toolbar`, `.input-actions` in
scope `data-v-e28f96e8` (**`AppSingleChatView`**, the page container) are **dead
CSS**: rules compiled for a template that no longer emits those classes. They are
byte-level leftovers of the composer card, which has since moved into
`AppChatComposer` and renamed to `composer-*`.

Scope-hash map (hashes change every build; the component does not):

| Component | v1.0.3 scope | v1.0.4 scope |
|---|---|---|
| `AppChatComposer` — **the composer** | `744a5cd2` | `a139dd89` |
| `AppSingleChatView` — page container | `8aca294f` | `e28f96e8` |

Corroboration: the `@keyframes` names in the `composer-*` rules carry the
component's own scope hash as their build suffix
(`animation:composer-spin-744a5cd2` → `composer-spin-a139dd89`), which pins the
mapping independently of the JS.

### Impact on the Compose port

Bounded, and less bad than it looks — the two families were near-duplicates:

- **The card is right by accident.** Every metric this file checked on
  `.input-wrapper` (`min-height:78px`, `padding:8px 12px 6px`, `gap:4px`,
  `border-radius:18px`, `var(--ink-bg-card)`, `1px solid var(--ink-input-border)`,
  `var(--ink-shadow-lg)`) is **identical** in `.composer-wrapper`. Those ✅ rows
  stand.
- **The textarea is specified from the dead rule.** The authoritative
  `.composer-textarea` differs concretely — see below. This is the actionable part.

### Release stability

The `composer-*` family holds **56 rule selectors** in both 1.0.3 and 1.0.4. Diffing
them rule-by-rule, exactly two differ, and both differ *only* in the `@keyframes`
build suffix (`.composer-spinner`, `.composer-voice-button--recording`). No
`composer-*` rule was added, removed or restyled between the two releases, so a
port built against either release is built against the same spec.

## Version-drift check

Prior composer work (`8163a5d`, `cf7a5ce`) was done against the v0.7.18 checkout,
while the closest pinned checkout for this APK is **v0.7.13**. `ChatInput.vue`
does differ between those tags:

| | v0.7.13 | v0.7.18 |
|---|---|---|
| Class family | `.input-*` | `.input-*` |
| Classes present | `input-area`, `input-wrapper`, `input-textarea`, `input-toolbar`, `input-top-bar`, `input-actions`, `send-button`, `attachment-thumb`, … | same set |
| Difference | — | adds `attachment-thumb-button` |

`ChatInput.vue`'s class vocabulary is stable across both versions, so the drift
concern was indeed benign.

But that comparison turned out to be **about the wrong component**. `ChatInput.vue`
is the upstream *web/desktop* composer; the mobile build does not render it. The
mismatch it was checked against (`input-*` in the APK stylesheet) is not the
shipped composer either — it is the dead `AppSingleChatView` copy documented
above. Reading `ChatInput.vue` is still useful for *structure* (slot layout,
which sub-element goes where), but never for class names or measurements on this
surface.

## `.composer-wrapper` — the composer card

| Property | Official | Compose | Status |
|---|---|---|---|
| `border-radius` | `18px` | `RoundedCornerShape(18.dp)` | ✅ match |
| `box-shadow` | `var(--ink-shadow-lg)` = `0 8px 24px rgba(0,0,0,.09)` | `shadowElevation = 8.dp` | 🔶 approximate |
| `background` | `var(--ink-bg-card)` | via ink token | ✅ |
| `border` | `1px solid var(--ink-input-border)` | referenced in comment; verify | 🔶 unverified |
| `min-height` | `78px` | not asserted | ❌ missing |
| `padding` | `8px 12px 6px` | `12.dp` horizontal, partial | 🔶 partial |
| `gap` | `4px` | `spacedBy(4.dp)` (inner row) | ✅ match |
| `max-width` | `var(--app-composer-max-width, 1100px)` + `margin:0 auto` | not asserted | ➖ harmless (compiled app is always ≤ 1100px wide) |

`min-height: 78px` is the notable gap: it is what stops the composer from
collapsing to a single text line, and Compose does not assert it. Worth fixing.

`shadowElevation` is an honest approximation, not a match — Compose elevation and
CSS `box-shadow` do not render identically, and `--ink-shadow-lg` differs by
theme (`rgba(0,0,0,.09)` light vs `.3` dark), which a fixed elevation cannot
express.

## `.composer-textarea` — the real gap

The Compose port's basis was the dead `.input-textarea`. The authoritative rule is:

```css
.composer-textarea{display:block;box-sizing:border-box;width:100%;min-height:24px;
  max-height:280px;overflow-y:auto;flex:0 0 auto;padding:1px 0 2px;
  color:var(--ink-text-primary);background:transparent;border:0;
  font-size:14px;line-height:21px;transition:height .12s ease}
```

| Property | `.input-textarea` (dead, what the port used) | `.composer-textarea` (**authoritative**) |
|---|---|---|
| `max-height` | *(none)* | **`280px`** — with `overflow-y:auto` |
| `flex` | `1` | `0 0 auto` |
| `padding` | `0` | `1px 0 2px` |
| `line-height` | `1.5` (= 21px at 14px, coincidentally equal) | `21px` (explicit) |
| `transition` | *(none)* | `height .12s ease` |
| `box-sizing` | *(unset)* | `border-box` |

Read together with `AppChatComposer`'s other rules, this is a **self-sizing
textarea**: JS writes an explicit `style="height:Npx"` and the CSS caps growth at
280px with an animated height, adding `.composer-textarea--resizing`
(`transition:none`) while the user drags the resize handle. A port that models the
field as `flex:1` + a fixed or unbounded height misses both the 280px cap and the
drag-resize animation. **Not yet verified against the Compose `BasicTextField`** —
this is the next thing to check on this surface.

## Authoritative sub-element spec (v1.0.4)

The whole family, so the next pass does not have to re-derive it. Sizes in px.

| Element | Spec |
|---|---|
| `.composer-toolbar-button`, `.composer-voice-button`, `.composer-send-button` | `30×30`, `border-radius:999px`, `color:var(--ink-text-secondary)` |
| `.composer-toolbar-button uni-image` | `16×16`, `filter:var(--ink-secondary-icon-filter, var(--ink-icon-filter))` |
| `.composer-voice-button>uni-image` | `20×20`, same filter |
| `.composer-send-button>uni-image` | `17×17` |
| `.composer-send-button` / `--active` | `#fff` on `--ink-text-muted` → `--ink-on-accent` on `--ink-accent` |
| `.composer-toolbar-button--active` | `color:var(--ink-accent); background:var(--ink-selected-bg)` |
| `.composer-action--pressed` / `--disabled` | `transform:scale(.94)` / `opacity:.38` |
| `.composer-toolbar` | `min-height:30px`, `gap:12px`, `margin-top:auto` |
| `.composer-toolbar-left`, `.composer-actions` | `gap:5px` (`.composer-actions` `gap:7px`) |
| `.composer-attachment` | `154×48`, `padding:5px 25px 5px 5px`, `gap:7px`, `radius:9px` |
| `.composer-attachment-thumb` / `-file-icon` | `38×38`, `radius:6px` |
| `.composer-attachment-strip` | `white-space:nowrap` (full width) |
| `.composer-status-bar` | `width:calc(100% - 28px)`, `min-height:27px`, `margin:0 auto 8px`, `radius:11px` |
| `.composer-resize-handle` | `36×16` at `top:-8px; left:14px`, `radius:999px`, `touch-action:none` |
| `.composer-spinner` | `13×13`, `1.5px` border, spins `.75s` |
| `.composer-stop-symbol` | `9×9`, `radius:2px` |
| `.composer-safe-area` | `height:var(--app-safe-area-bottom, env(safe-area-inset-bottom)); min-height:12px` |
| `.composer-voice-button--recording` | `#d74a4a` on `rgba(215,74,74,.12)`, breathe animation `1.4s` |
| `.composer-voice-time` | `min-width:29px`, `#d74a4a`, tabular numerals |
| `.composer-attachment-panel` | `radius:18px 18px 0 0`, `padding:15px 20px 16px` |
| `.chat-composer-area` | `position:relative; z-index:80; padding:8px 12px 0` (20px l/r in the phone breakpoint) |

Note `#d74a4a` and `#fff` are literal rather than `--ink-*` — the recording state
and the send glyph are the deliberate exceptions on this surface, like the
terminal's palette.

## Why this page is only partially audited

The composer is a composite of ~15 sub-elements (attachment strip, toolbar,
model chip, voice button, send button, context meter, resize handle). Auditing it
properly means extracting each one's rule from `pages-index.css` and pairing it
with the Compose node that renders it. This pass established the ground truth,
resolved the class-family question, and checked the container.

Still unpaired in Compose: `.composer-toolbar`, `.composer-toolbar-button`
(+ `--active`/`--pressed`/`--disabled`), `.composer-voice-button` (+ `--recording`
/ `--processing` / `composer-voice-time`), `.composer-send-button`
(+ `--active`/`--loading`/`composer-spinner`), `.composer-resize-handle`
(+ `--active`/`--disabled`), `.composer-attachment*` (13 rules),
`.composer-status-bar`, `.composer-safe-area`, and the `input-context-meter*`
family (`AppSingleChatView`'s own, which *is* live).

## Reproducing

```sh
# the authoritative family, v1.0.4
for s in composer-wrapper composer-textarea composer-toolbar composer-toolbar-button \
         composer-voice-button composer-send-button composer-resize-handle \
         composer-attachment composer-safe-area; do
  echo "### .$s"
  scripts/apk/css-rules.py docs/reference/apk-1.0.4/css/pages-index.css ".$s"
done

# confirm the input-* copy is dead (expect: no hits in the render code)
grep -c '"input-wrapper"' latest/decoded-v104/assets/apps/__UNI__1F41684/www/app-service.js
```
