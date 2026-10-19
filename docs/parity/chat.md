# Chat / Composer parity record

Ground truth, in this order:

| Source | What it settles |
|---|---|
| `docs/reference/apk-1.0.4/css/pages-index.css` (baseline) and `…/apk-1.0.3/…` | the shipped stylesheet — every size, radius and colour below |
| ↳ scope `data-v-744a5cd2` (v1.0.4: `a139dd89`) = **`AppChatComposer`** | the composer card and toolbar button family (`.composer-wrapper`, `.composer-textarea`, `.composer-toolbar-button`, `.composer-voice-button`, `.composer-send-button`) — the family the app actually renders |
| ↳ scope `data-v-8aca294f` (v1.0.4: `e28f96e8`) = **`AppSingleChatView`** | the page container. Its `.input-*` composer rules are **dead CSS** — see the warning below |
| ↳ scope `data-v-b5cd62b3` (v1.0.4: `8390f519`) = **`ConversationDrawer`** | the session list — `.session-item`, `.session-unread-dot`, `.drawer-list-state`, `.session-running-track` |
| ↳ scope `data-v-c0c550c3` (v1.0.4: `45c4d322`) = **`AppMessageList`** | the message list, including the v1.0.4-only `.chat-empty-agent` state |
| `/root/Hermes任务文件/HStudioDirect/ref-v0.7.13/packages/client/src/components/hermes/chat/` | render structure only — `ChatInput.vue`, `MessageItem.vue`, `ChatPanel.vue`, `ToolRunCard.vue`, `LiveReasoningStatus.vue`, `MessageQueueFloatPanel.vue`, `SessionListItem.vue` |
| `docs/OFFICIAL_PARITY_MATRIX.md` §B | the row this pass was scoped against |

> **⚠️ Composer class family (corrected 2026-09-19).** An earlier version of this
> table called scope `data-v-8aca294f` "the official mobile composer stylesheet"
> and read `.input-wrapper` / `.input-textarea` out of it. Those rules are
> **dead**: no element in the bundle ever receives an `input-wrapper` class
> (checked statically and against dynamic construction), and the component that
> owns that scope, `AppSingleChatView`, emits only its own `input-context-*`
> meter. The shipped composer is `AppChatComposer`, emitting `composer-*`. Card
> measurements happen to agree between the two families, but the textarea does
> not — full account in `docs/parity/composer.md`.

> **⚠️ The same liveness check, applied to the rest of this file (2026-09-19).**
> Every selector named here was re-tested against `app-service.js` — does some
> component actually emit this class, statically or through `normalizeClass` /
> string building? Nine did not: `.empty-state`, `.empty-logo`,
> `.session-item-unread-dot`, `.session-loading`, `.session-empty`,
> `.user-message-author`, `.session-items` in prose, and — found on a second,
> stricter pass that tested *class tokens* rather than substrings — `.chat-header`
> / `.header-session-title` (each occurs **0 times** in both releases and has no
> CSS rule). Those rows have been rewritten against the live names —
> `.chat-empty-agent` / `-logo` / `-name`, `.session-unread-dot`,
> `.drawer-list-state`, `.session-list`, `.message-author.single-user-author`,
> and the real top bar `AppNavigationBar` (`.app-navigation*`). Details at each
> table.
>
> **Rule for future passes: a class name in the stylesheet is not evidence that
> anything renders it.** Before treating a rule as the spec for a surface,
> confirm an emitter — and confirm the rule is not shadowed by a live
> near-duplicate in another component's scope, which is exactly how the composer
> error happened. The check is reproducible and re-runnable:
>
> ```sh
> scripts/apk/class-liveness.py --release both /root/Hermes任务文件/HStudioDirect \
>     docs/parity/*.md docs/OFFICIAL_PARITY_MATRIX.md
> ```
>
> It exits non-zero for any cited class that no component emits and no note in
> the same document explains. Substring matching is not an acceptable substitute:
> `new-chat-header` contains `chat-header`.

Why v0.7.13: the baseline APK (v1.0.4; the constant is identical in v1.0.3,
contains `const Ar="0.7.13"` in
`assets/apps/__UNI__1F41684/www/app-service.js`. Earlier chat work was partly
compared against v0.7.18 source, so this pass re-checks against the closest
pinned checkout, v0.7.13, **and** the APK stylesheet.

> **Read that constant correctly (corrected 2026-09-19).** `Ar` is *not* the
> version of the bundled client. It is the default argument of the semver
> comparator `_r(e, t = Ar)` that `Vr()` uses to reject an out-of-date server —
> i.e. `0.7.13` is the **minimum Studio server version**, and it is the same
> literal that appears in the `studio_version_unsupported` message. See
> `docs/reference/apk-1.0.3/README.md` § *Web client version pin*. So `v0.7.13`
> is a *convenient nearby checkout* for the upstream-derived majority of the
> client, not "the version the APK ships" — the bundle identifies no client
> version at all. Where the two disagree, **the APK stylesheet wins**.

## Implementation

`android/app/src/main/java/xyz/rflg/hstudiodirect/MainActivity.kt` —
`Composer()` (≈2471), `ComposerActionButton()` (≈2829), `ConversationScreen()`
(≈1509), `MessageBubble()` (≈1873), `ThinkingTimeline()` (≈2028).

## Composer — values verified against the official stylesheet

Extracted rule-by-rule from `pages-index.css`; each official value below was read
out of the stylesheet in this pass, not inferred.

| Selector | Official value | Compose | Status |
|---|---|---|---|
| `.chat-composer-area` | `padding: 8px 12px 0` | `start 12 / end 12 / top 8` | ✅ match |
| `.chat-composer-area` (gutter block) | `padding-right/left: 20px` in the `min-width:769px` media block | not applied on phones | ✅ n/a (phone-first) |
| `.chat-composer-area` bottom | `padding-bottom: 0` + separate `.composer-safe-area` block (`height: var(--app-safe-area-bottom, env(safe-area-inset-bottom)); min-height: 12px`) | `navigationBarsPadding()` + 7dp | 🔶 approximation (no `--app-safe-area-bottom` override) |
| `.composer-wrapper` | `min-height: 78px` | `heightIn(min = 78.dp)` | ✅ **fixed this pass** |
| `.composer-wrapper` | `padding: 8px 12px 6px` | `top 8 / start 12 / end 12 / bottom 6` | ✅ **fixed this pass** |
| `.composer-wrapper` | `flex-direction: column; gap: 4px` | `Column` + `spacedBy(4.dp)` on inner rows | ✅ match |
| `.composer-wrapper` | `background: var(--ink-bg-card)` | `inkCardBg(inkDark)` | ✅ match |
| `.composer-wrapper` | `border: 1px solid var(--ink-input-border)` | `BorderStroke(1.dp, inkInputBorder(inkDark))` | ✅ match |
| `.composer-wrapper` | `border-radius: 18px` | `RoundedCornerShape(18.dp)` | ✅ match |
| `.composer-wrapper` | `box-shadow: var(--ink-shadow-lg)` | `shadowElevation = 8.dp` | 🔶 approximation (CSS box-shadow ≠ Compose elevation) |
| `.composer-textarea` | `min-height: 24px` | `heightIn(min = 24.dp)` | ✅ match |
| `.composer-textarea` | `background: transparent` | `BasicTextField` with no container | ✅ match |
| `.composer-textarea` | `border: 0; padding: 1px 0 2px` | no container, **no inner padding either** | ❌ the dead `.input-textarea` basis said `padding: 0` |
| `.composer-textarea` | `font-size: 14px` | `14.sp` | ✅ match |
| `.composer-textarea` | `line-height: 21px` (stated outright) | `lineHeight = 21.sp` | ✅ match (derived as `1.5 × 14`; same value either way) |
| `.composer-textarea` | `max-height: 280px; overflow-y: auto; flex: 0 0 auto; box-sizing: border-box; transition: height .12s ease` | not asserted (`fillMaxWidth()` only) | ❌ **missing** — the dead basis said `flex: 1`. See `composer.md` |
| `.composer-placeholder` | `color: var(--ink-text-muted)` | `inkTextMuted(inkDark)` + `nowrap` ellipsis | ✅ match |
| `.composer-toolbar` | `min-height: 30px; margin-top: auto` | `Arrangement.SpaceBetween` pins the toolbar to the bottom | ✅ match |
| `.composer-toolbar` | `justify-content: space-between; gap: 12px` | `SpaceBetween`; row gap not asserted | 🔶 partial |
| `.input-context-status` | `display: flex; align-items: center; gap: 6px; color: var(--ink-text-muted)` | `Row` + `spacedBy(6.dp)`, muted | ✅ **fixed this pass** (was a `Column`) |
| `.input-context-label` | `max-width: 112px; font-size: 9px; line-height: 14px; ellipsis; nowrap` | `widthIn(max = 112.dp)`, `9.sp`/`14.sp`, ellipsis | ✅ **fixed this pass** |
| `.input-context-meter` | `width: 34px; height: 3px; border-radius: 999px; background: var(--ink-border)` | `34.dp`×`3.dp`, `999.dp` radius, `inkBorder` track | ✅ **fixed this pass** (was a full-width bar, 84–122dp) |
| `.input-context-meter-fill` | `height: 100%; background: var(--ink-text-muted)` | `fillMaxHeight().fillMaxWidth(pct)` | ✅ **fixed this pass** (fill was the *label's* colour, not muted) |
| `.input-context-status--warning` | `color: #d59a2d` (whole row) | `CONTEXT_WARNING = 0xFFD59A2D` on row + fill | ✅ **fixed this pass** (was `0xFFC28A30`) |
| `.input-context-status--danger` | `color: var(--ink-error)` (whole row) | `inkError(inkDark)` on row + fill | ✅ **fixed this pass** (was `colorScheme.error`) |
| `.input-context-status--pressed` / `--disabled` | `opacity: .66` / `.52` | not applied | 🔶 unverified |
| `.input-context-status` thresholds | `app-service.js`: `--warning: pct > 60 && pct <= 80`, `--danger: pct > 80` | same thresholds | ✅ match (confirmed in the bundle this pass) |
| `.input-context-status` interaction | `role="button"`, aria-name 点击编辑上下文长度 | rendered read-only, no click handler | ⛔ intentionally absent (editing the context window is a Studio-side control this build does not open) |
| `.toolbar-button[data-v-8aca294f]` — **dead CSS** | `min-width: 35px; height: 28px; padding: 0 4px 0 6px; gap: 3px` has **no live counterpart**: the shipped composer's buttons are a uniform 30×30 | — | ➖ drop from the audit |
| `.toolbar-button--disabled` / `.voice-button--disabled` — **dead CSS** | `opacity: .38`; live equivalent is `.composer-action--disabled` (`opacity:.38; pointer-events:none`) | not compared | 🔶 unverified (use the `.composer-*` rows below) |
| `.composer-attachment` | `width: 154px; height: 48px; padding: 5px 25px 5px 5px; gap: 7px; border-radius: 9px; background: var(--ink-bg-card-hover)` | `Surface(154×48, radius 9, card-hover) + Row(padding 5/25/5/5, gap 7)` | ✅ match |
| `.composer-attachment--image` | `width: 48px; padding: 0; background: transparent` | `48.dp` Surface, `Color.Transparent`, zero padding | ✅ match |
| `.composer-attachment-thumb` / `.composer-attachment-file-icon` | `width: 38px; height: 38px; border-radius: 6px` | `38.dp` Box, `RoundedCornerShape(6.dp)` | ✅ match |
| `.composer-attachment--image .composer-attachment-thumb` | `width/height: 48px; border-radius: 8px` | `48.dp` Box, `RoundedCornerShape(8.dp)` | ✅ match |
| `.composer-attachment-file-icon` | `display:flex; align-items:center; justify-content:center; color:var(--ink-text-muted); background:var(--ink-bg-code); font-size:11px; font-weight:700` | `inkCodeBg` fill, `inkTextMuted` 11sp/700, centered | ✅ match |
| `.composer-attachment-copy` | `display:flex; min-width:0; flex:1; flex-direction:column; gap:2px` | `Column` + `Modifier.weight(1f)` + `spacedBy(2.dp)` | ✅ match |
| `.composer-attachment-copy uni-text` | `overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:var(--ink-text-primary); font-size:11px; line-height:15px` | `maxLines=1, overflow=Ellipsis`, 11sp/15sp, primary | ✅ match |
| `.composer-attachment-copy uni-text:last-child` | `color:var(--ink-text-muted); font-size:9px; line-height:13px` | intentionally absent | ⛔ Upload has no byte-size field |
| `.composer-attachment-remove` | `position:absolute; top:3px; right:5px; width:17px; height:17px; color:var(--ink-text-muted); font-size:16px; line-height:16px; text-align:center` | `offset(0,3) + padding(end=5)`, 17.dp box, 16.dp muted Close | ✅ match |
| `.composer-attachment--image .composer-attachment-remove` | `top:-5px; right:-5px; color:#fff; background:rgba(0,0,0,.66); border-radius:999px; font-size:14px; line-height:16px` | `offset(5,-5) + CircleShape + rgba(0,0,0,.66) + 14dp white Close` | ✅ match |
| `.composer-toolbar-button` / `-voice-button` / `-send-button` (`744a5cd2`) | `30×30; border-radius: 999px; color: var(--ink-text-secondary)` | not compared | 🔶 unverified |
| `.composer-send-button` | `color: #fff; background: var(--ink-text-muted)` | not compared | 🔶 unverified |
| `.composer-send-button--active` | `color: var(--ink-on-accent); background: var(--ink-accent)` | not compared | 🔶 unverified |
| `.composer-send-button > uni-image` | `17×17` | not compared | 🔶 unverified |
| `.composer-send-button--active uni-image` | `filter: var(--ink-send-icon-filter)` | not compared | 🔶 unverified |
| `.composer-toolbar-button uni-image` | `16×16; filter: var(--ink-secondary-icon-filter, var(--ink-icon-filter))` | not compared | 🔶 unverified |
| `.composer-toolbar-button--active` | `color: var(--ink-accent); background: var(--ink-selected-bg)` | not compared | 🔶 unverified |

### The one structural correction this pass made

`.composer-textarea` is `background: transparent; border: 0`. The previous
implementation used an `OutlinedTextField` with `inkInputBg` fill, an outline in
`--ink-input-border`, and its own `RoundedCornerShape(10.dp)` — i.e. **a second
rounded box nested inside `.composer-wrapper`**, which the official composer does
not have. It is now a `BasicTextField` inside a transparent `Box`, so the only
painted surface is `.composer-wrapper` itself. The 10px radius and `inkInputBg`
fill were not "close enough": the official card is one 18px-radius surface, not a
box in a box.

Two details of that rule are still wrong, and they came from reading the dead
`.input-textarea` copy (see the warning at the top of this file):

- `padding` is **`1px 0 2px`**, not `0`.
- the field is **self-sizing and capped**: `flex: 0 0 auto; max-height: 280px;
  overflow-y: auto; transition: height .12s ease`, with a
  `.composer-textarea--resizing` state that drops the transition while the user
  drags `.composer-resize-handle`. `AppChatComposer` writes an explicit
  `style="height:Npx"` — the height is JS-driven, not CSS-driven. A port using
  `fillMaxWidth()` + `flex: 1` semantics has neither the cap nor the drag-resize.

Verified names for the commit that follows this doc: the composer is
`AppChatComposer` (scope `a139dd89` in v1.0.4), and the card/textarea/toolbar
rules above are the ones it renders.

## HistoryView (session list) — values verified against the official stylesheet

`SessionRow()` (≈1067) and the `ChatsScreen` list body (≈944) in `MainActivity.kt`,
checked against `pages-index.css` scope `b5cd62b3` (v1.0.4: `8390f519`) — the
**`ConversationDrawer`** component — and `SessionListItem.vue`.

| Selector | Official value | Compose | Status |
|---|---|---|---|
| `.session-item` | `display:flex; align-items:center; width:100%; margin-bottom:2px; padding:8px 10px; color:var(--ink-text-secondary); border-radius:6px` | `Row`-rooted `Column`, `clip(RoundedCornerShape(6.dp))`, `padding(horizontal=10, vertical=8)`, list `spacedBy(2.dp)` for the `margin-bottom` | ✅ match (was 13/11 padding, no radius, in a grouped card) |
| `.session-item--active` | `color:var(--ink-text-primary); background:var(--ink-bg-secondary)` | `inkSecondaryBg` fill + `inkTextPrimary` title | ✅ match |
| `.session-item--active .session-title` | `font-weight:550` | `FontWeight(550)` on the title | ✅ match (was `W500`) |
| `.session-item--pressed` | `background:var(--ink-pressed)` | `combinedClickable` ripple | 🔶 approximation (no explicit pressed tint) |
| `.session-title` | `font-size:13px; line-height:18px; text-overflow:ellipsis; white-space:nowrap` | `13.sp`/`18.sp`, `maxLines=1`, `Ellipsis` | ✅ match (was `titleSmall`, `maxLines=2`) |
| `.session-time` | `flex-shrink:0; color:var(--ink-text-muted); font-size:11px; line-height:16px` | `labelSmall` 11sp/16sp, `inkTextMuted` | ✅ match (was `onSurfaceVariant`) |
| `.session-unread-dot` | `width:6px; height:6px; flex:0 0 auto; border-radius:50%; background:var(--ink-accent); box-shadow:0 0 0 3px var(--ink-selected-bg)` | 6dp `CircleShape`, `inkAccent` fill (reused as the running marker) | ✅ fill/radius match — **name corrected**; the 3px `--ink-selected-bg` halo is not asserted |
| `.session-list` | `padding:0 6px 12px` | list `contentPadding` horizontal 6dp + the `StudioHorizontalPadding` gutter | 🔶 approximation |
| `.drawer-list-state` — loading / empty / error | `display:flex; align-items:center; justify-content:center; min-height:120px; padding:20px; flex-direction:column; gap:8px; color:var(--ink-text-muted); font-size:11px; text-align:center`; the spinner is a child `.drawer-list-spinner` (`15×15; border:2px solid var(--ink-border); border-top-color:var(--ink-text-secondary)`) | `EmptyNote` — a centered `Box` with `onSurfaceVariant`; `LoadingRow` — 16dp padding + a 22dp `CircularProgressIndicator` | 🔶 approximation, four deltas: no `min-height:120px`, no 8dp column, the spinner is **22dp not 15×15**, and `onSurfaceVariant` is neither token (`#666666` light = `--ink-text-secondary`; `#999999` dark matches nothing — `--ink-text-muted` is `#858987`) |
> **⚠️ Three more dead-class corrections (2026-09-19)** — found with the same
> liveness check that fixed the composer in `composer.md`. Each of these names was
> taken from the stylesheet or from a desktop `.vue` file without confirming that
> anything in the shipped bundle emits it:
>
> | What this file said | Reality |
> |---|---|
> | `.session-item-unread-dot` | The class is **`.session-unread-dot`**. `session-item-unread-dot` occurs **0 times** in `app-service.js` (both releases). The values were right; the halo was missing. |
> | `.session-loading` / `.session-empty` | **Neither exists** — 0 occurrences in either bundle and no CSS rule. All three session-list states share one container, **`.drawer-list-state`**, distinguished by `--error` and by a child `.drawer-list-spinner`. |
> | `.session-items` (prose below) | **Does not exist** (0 occurrences, no rule). The real container is `.session-list` (`padding:0 6px 12px`) inside `.session-scroll` (`flex:1; min-height:0`). |
>
> The live markup, from the v1.0.4 `ConversationDrawer` render function:
>
> ```js
> D.value ? (… class:"drawer-list-state", [ … class:"drawer-list-spinner",
>              text = $t("正在读取") ])                             // loading
>          : T.value ? (… class:"drawer-list-state drawer-list-state--error",
>              [ text = $t(T.value), class:"drawer-list-retry" ])   // error
>          : (… class:"drawer-list-state", [ text = $t(F.value) ])  // empty
> ```
>
> Two consequences for the port:
>
> - **`session-item--unread` / `--running` / `--failed` are emitted but carry no
>   CSS rule.** Only `--active` and `--pressed` are styled. They are state hooks,
>   not visual specifications — do not port them as a treatment. The visible
>   signals are `.session-unread-dot` in the title row and `.session-running-track`
>   under the title.
> - **The dot has two states, not one:** `.session-unread-dot--failed` is
>   `background:var(--ink-error); box-shadow:0 0 0 3px var(--ink-error-soft)`, and
>   the drawer picks it dynamically —
>   `normalizeClass(["session-unread-dot", {"session-unread-dot--failed": …}])`.
>
> As in `composer.md`: `SessionListItem.vue` / `MessageItem.vue` are the
> **desktop** components. They are useful for structure (slot order, which
> sub-element goes where) and never for class names or measurements on this surface.

The grouped-card + divider list (`StudioGroupedCard` + `StudioCardDivider`) was
replaced with standalone 6dp-radius rows spaced 2dp apart, which is how the
official `.session-item` rows sit in `.session-list` inside `.session-scroll` (each its own
`margin-bottom:2px` tile, not a single card with hairline dividers). The desktop
`SessionListItem.vue` carries an 18px per-agent logo, a 16px profile avatar, and
a category tag in a `.session-item-agent-row`; this mobile build keeps a 34dp
profile avatar as the leading mark (the 18px agent-logo needs a per-agent SVG
asset not bundled here) and drops the trailing chevron the official row does not
have. That is a documented adaptation, not a parity claim.

## ChatView message list — values verified against the official stylesheet

`MessageBubble()` (≈1935), `ConversationScreen()` empty state (≈1687),
`SessionRunningBar()` (≈1170) in `MainActivity.kt`, checked against
`pages-index.css` scope `c0c550c3` (v1.0.4: `45c4d322`) — the **`AppMessageList`**
component — and `MessageItem.vue`.

| Selector | Official value | Compose | Status |
|---|---|---|---|
| `.message.user .msg-body` | `max-width:75%` | `fillMaxWidth(0.75f)` | ✅ **fixed this pass** (was `fillMaxWidth(1f)`) |
| `.message.assistant .msg-body` | `max-width:80%` | `fillMaxWidth(0.8f)` | ✅ **fixed this pass** (was `fillMaxWidth(1f)`) |
| `.message-bubble` | `padding:10px 14px; border-radius:10px; background:var(--ink-bg-message)` | `Card` `padding(14,10)`, `RoundedCornerShape(10.dp)`, `inkMessageBg` | ✅ match |
| `.message-bubble--error` | `color:var(--ink-error); background:var(--ink-error-soft); border:1px solid var(--ink-error-soft)` | `inkErrorSoft` fill + `inkError` text | ✅ match |
| `.message-author` | `display:flex; min-height:22px; margin:0 0 4px 2px; align-items:center; **gap:4px**` — the `--ink-text-secondary` / 12px this row used to carry here live on `.message-author-name`, below | `Row` `padding(start=2, bottom=4)`, 12sp/16sp Medium, `inkTextSecondary` | ✅ **fixed this pass** (was an `.agent-badge` pill, 8sp/600) — ❌ **but `gap` is `4px`, not `6px`**: the port hard-codes `Spacer(Modifier.width(6.dp))` on both sides, and the comment at `MainActivity.kt:2019` repeats the wrong value |
| `.message-author-name` | `color:var(--ink-text-secondary); font-size:12px; font-weight:500; line-height:16px` | 12sp/16sp `FontWeight.Medium`, `inkTextSecondary` | ✅ **fixed this pass** |
| `.message-author.single-user-author` | `margin-right:2px; margin-left:0; justify-content:flex-end` | the user branch of the same `Row`: `Arrangement.End` | ✅ alignment matches — 🔶 the margin swap is not asserted (the port pads `start=2.dp` for both roles). **Live name corrected** (was `.user-message-author`, 0 occurrences) |
| `.msg-avatar` (assistant) | `width:22px; height:22px; border-radius:50%` | `ProfileAvatar(size=22.dp)`, now inside the author row above the bubble | ✅ **fixed this pass** (was beside the bubble) |
| `.chat-empty-agent` | `display:flex; flex-direction:column; align-items:center; gap:12px; max-width:100%; color:var(--ink-text-muted)` | `Column` centered, `spacedBy(12.dp)`, `inkTextMuted` | ✅ layout matches — **class name corrected** (see below); this state is **new in v1.0.4** |
| `.chat-empty-agent-logo` | `width:48px; height:48px; border-radius:12px; overflow:hidden; **opacity:.4**` | `ProfileAvatar(size=48.dp)` in a `.alpha(0.25f)` Box | ❌ **two deltas:** opacity is `.4` (not `.25`) and the logo is clipped to a **12dp** rounded rect |
| `.session-running-track` | `width:100%; height:2px; margin-top:2px; background:var(--ink-selected-bg); border-radius:999px` | `fillMaxWidth`, `height(2.dp)`, `padding(top=2.dp)`, `inkSelectedBg`, `999.dp` | ✅ **fixed this pass** (track was `surfaceVariant α.3`) |
| `.session-running-light` | `width:50%; rainbow gradient; transform:translate3d(-110%,0,0); animation 1.8s linear` | `fillMaxWidth(0.5f)`, rainbow `linearGradient`, `graphicsLayer{translationX = size.width*(p*2.2-1.1)}`, 1800ms | ✅ **fixed this pass** (offset was a fixed dp guess) |

Two rows above were also reading classes that do not exist — **`.empty-state`**
and **`.empty-logo` occur 0 times** in `app-service.js` in either release, and
neither has a CSS rule. The state itself is real; the names were not.

The live spec (v1.0.4) is three classes, and the wrapper is a list modifier:

```js
// AppMessageList (v1.0.4, scope data-v-45c4d322)
f = computed(() => variant==="single" && !rowOnly && Boolean(sessionKey)
                 && !loading && !error && !messages.length && !h.value)
…normalizeClass(["app-message-list", { … "app-message-list--empty": f.value }])
…
f.value ? (… class:"chat-empty-agent", [
    image({class:"chat-empty-agent-logo", src: assistantAvatar, mode:"aspectFit"}),
    text({class:"chat-empty-agent-name"},
         $t("开始与 {agent} 对话").replace("{agent}", assistantName==="Hermes" ? "Hermes Agent" : assistantName))
  ])
```

| Rule | Value |
|---|---|
| `.app-message-list--empty` | `height:100%; display:flex; align-items:center; justify-content:center` |
| `.chat-empty-agent` | `flex column; align-items:center; gap:12px; max-width:100%; color:var(--ink-text-muted)` |
| `.chat-empty-agent-logo` | `48×48; border-radius:12px; overflow:hidden; opacity:.4` |
| `.chat-empty-agent-name` | `max-width:100%; text-align:center; overflow-wrap:anywhere; font-size:14px` |

**v1.0.3 renders this state differently, and this matters for the baseline.** In
v1.0.3 there is no `.chat-empty-agent*` and no `.app-message-list--empty`; an
opened-but-empty session falls through to the second branch of the same
`AppMessageList` template:

```js
t.emptyText && !t.sessionKey ? (… class:"chat-state", [ text = $t(t.emptyText) ])
```

i.e. a **`.chat-state` line** — `display:flex; min-height:86px; align-items:center;
justify-content:center; gap:8px; color:var(--ink-text-muted); font-size:12px` — with
no logo at all. So the avatar empty state is a **v1.0.4 addition**, and the earlier
pass that introduced it was building toward v1.0.4 while citing a class from
nowhere. Against v1.0.3 it would be a fabrication; against v1.0.4 it is right in
shape and off in two numbers.

### What this pass corrected in the message list

- **Bubble width.** The previous build let every bubble span the full row
  (`fillMaxWidth(1f)`) with a comment claiming the 75%/80% caps belonged to the
  composer's `8aca294f` scope. They do not — `.message.user .msg-body{max-width:75%}`
  and `.message.assistant .msg-body{max-width:80%}` are message-list rules in
  scope `c0c550c3`, shadowing an earlier `max-width:100%` further up the cascade.
- **Author row.** The assistant sender was rendered as an `.agent-badge` pill
  (8sp/600, `--ink-bg-secondary`) with the 22dp avatar floating beside the
  bubble. The official `.message-author` is a 12sp/500 `--ink-text-secondary`
  text line above the bubble, with the 22dp `.msg-avatar` inside that row. Both
  roles now have an author row; the user row mirrors
  `.message-author.single-user-author` (`justify-content:flex-end`; name + avatar,
  right-aligned). One number in that row was still wrong: the official `gap` is
  **`4px`**, while the port's `Spacer` is `6.dp` — the `.message-author` row in
  the table above carries it.
- **Empty conversation.** Replaced the `💬` emoji with the agent-logo state.
  The layout (centered column, `gap:12dp`, `--ink-text-muted`) and the 14sp name
  line match `.chat-empty-agent` / `-name`, but the spec was cited under the
  non-existent `.empty-state` / `.empty-logo` names, and the logo is **`opacity:.4`
  clipped to a `12dp` rounded rect** — the port has `.25f` and no radius. That is a
  v1.0.4-only state; v1.0.3 shows a plain `.chat-state` line here (see above).
- **Running bar.** The `.session-running-track` fill is `--ink-selected-bg`
  (was `surfaceVariant` α.3), and the light band now sweeps `-110%→+110%` of
  its own width via `graphicsLayer` `translationX` (was a fixed
  `progress*200-50 dp` offset that did not track the band width).

`ToolRunCard()` (≈2254), `ToolRunItem()` (≈2383) and `ThinkingTimeline()` (≈2069)
were already aligned to `.tool-run-card`/`.tool-run-header`/`.tool-run-items`
and `.thinking-block`/`.thinking-header` in a prior pass and re-checked against
the stylesheet this pass; no change was needed.

## Not yet done (the honest remainder)

- The whole toolbar button family: `.composer-toolbar-button`
  (+ `--active`, `--pressed`, `--disabled`), `.composer-send-button`
  (+ `--active`, `--loading`, `composer-spinner`), `.composer-voice-button`
  (+ `--recording`, `--processing`, `composer-voice-time`),
  `.composer-resize-handle` (+ `--active`, `--disabled`), and the
  `--ink-send-icon-filter` / `--ink-secondary-icon-filter` recolouring. (Do **not**
  audit the dead `.toolbar-button` / `.send-button` / `.voice-button` rules.)
- The official **top bar** is **`AppNavigationBar`** — the bundle renders
  `.app-navigation` (`--ink-bg-primary`, a 12px gradient fade below it) with a
  `--app-safe-area-top` spacer and a `--app-navigation-content-height` (46px)
  row: `padding:3px 14px`, a 58×38 `.navigation-side--left` / `--right` pair, a
  centered `.navigation-title-wrap` (`max-width:calc(100% - 182px)`, `gap:6px`)
  holding a 15px/650 `.navigation-title`, and a 12px `.navigation-title-spinner`
  while `loading`. It is not yet matched — `ConversationTopBar` uses a Material
  `TopAppBar` with a left-aligned title, a 33dp avatar and a second runtime line,
  where the official bar shows one centered title. (The title is suppressed by
  the component's `hideTitle` prop, **not** by a media query.)
  > Corrected 2026-09-19: this bullet previously cited a "`.chat-header`
  > `@media (max-width:768px)`" rule that "hides `.header-session-title`". Neither
  > class exists — in `pages-index.css` **or** `app-service.js`, in either
  > release — and no rule in the bundle carries that padding. The name passed an
  > earlier substring test only because `new-chat-header` contains `chat-header`;
  > the real mobile header is `AppNavigationBar`.
- The official **streaming indicator** (`LiveReasoningStatus` — `.thinking-status`
  shimmer label + `.live-reasoning-detail` 26px pill) is not yet replicated as a
  list-level indicator; `ThinkingTimeline` covers the in-bubble `.thinking-block`
  but the standalone `streaming-indicator` row is still a plain spinner.
- `MessageQueueFloatPanel.vue` (`.queue-float-panel`) has not been compared; the
  current queue rendering is a horizontal scroll of `Surface` chips, not the
  official floating panel.

## Verification

| Check | Result |
|---|---|
| Official CSS values vs `MainActivity.kt` | read out of `pages-index.css` rule by rule in this pass — v1.0.3 scopes `744a5cd2` / `8aca294f` / `b5cd62b3` / `c0c550c3`, v1.0.4 `a139dd89` / `e28f96e8` / `8390f519` / `45c4d322` |
| Kotlin compile | **not run on this host** |
| Unit tests | **not run on this host** |
| Device/emulator | **not available** |

### Build and test limits

Unchanged from `docs/parity/baseline-tests.md`: this host is aarch64 and AGP 8.9.2
resolves an x86-64-only `aapt2`, so nothing here can be compiled locally. The
authoritative check is the Android CI workflow, which runs
`testDebugUnitTest` + `lintDebug` + `assembleRelease` on x86-64 `ubuntu-latest`.

**No claim in this document is backed by a screenshot.** The last device-verified
pass was v1.9.2 (`releases/v1.9.2-*.png`); nothing after it has been run on a
device.
