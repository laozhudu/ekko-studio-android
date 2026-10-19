# Official HStudio App — Parity Matrix

Baseline: **official HStudio APK v1.0.4** (`com.ekkostudio.ai`, versionCode 123,
SHA-256 `af47a968…fe5c7`) — see `docs/reference/apk-1.0.4/README.md` for
provenance. Superseded v1.0.3 (`bf069da2…20d62`) on 2026-09-18; that extraction
is kept in `docs/reference/apk-1.0.3/` for diffing. This is the vendor-signed
original, *not* this project's own `HStudio-Direct-v1.9.1.apk`.

## What the official app actually is

This matters more than any individual row, because it sets the size of the task.
The official APK is a **uni-app shell wrapped around the Hermes Studio web
client**. It contains exactly eight native pages:

| Route | Role |
|---|---|
| `pages/bootstrap` | splash / boot handoff |
| `pages/login` | account sign-in |
| `pages/login-devices` | **new in v1.0.4** — signed-in device manager (cloud account) |
| `pages/change-password` | **new in v1.0.4** — cloud-account password change |
| `pages/devices` | device list, add-device pairing, account menu |
| `pages/index` | **hosts the full Studio web client** |
| `pages/about` | about / legal |
| `pages/location-picker` | geolocation helper (uni-app system page) |

Six of those are genuinely native. `pages/index` is not a native screen at all —
its stylesheet is 541 KB and is the compiled output of the web client, whose
feature surface is **38 views and 139 components**.

So "replicate the official app" means two different jobs with two different
ground truths:

- **Shell pages** → ground truth is the APK's own compiled CSS/JS. The uni-app
  source is closed; nothing else specifies these screens.
- **Chat and everything behind it** → ground truth is upstream **v0.7.13** Vue
  source (pinned at `/root/Hermes任务文件/HStudioDirect/ref-v0.7.13`) for the
  upstream-derived majority, and the APK's compiled CSS for the vendor's private
  mobile layer (terminal, incoming files, paging paddle) — see §B2.

## Verification legend

Status here is deliberately split, because "the file exists" and "it matches the
official pixels" are different claims and this project has previously conflated
them.

- **Surface** — does a Compose implementation of this area exist?
  `yes` / `partial` / `no`.
- **Verified** — has it been checked against official ground truth?
  - `css` — compared line-by-line against the APK stylesheet
  - `src` — compared against pinned v0.7.13 Vue source
  - `name` — **inferred from the Compose screen name only; not yet compared**
  - `no` — never checked

> **No row in this document is verified by a device screenshot.** No emulator or
> physical device was available (see *Verification limits*). Every `css`/`src`
> claim below is a static comparison of stylesheet or source values. Static
> comparison cannot catch rendering, font-metric, or platform-inset differences.

---

## Part A — Native shell pages

These are the screens the official app implements natively, so they are the most
directly comparable and the highest-value parity work.

### A1. Login (`pages/login`)

Ground truth: `css/pages-login.css`. The phone layout is the
`@media screen and (max-width: 600px)` block — the app is phone-first, and the
unqualified rules are the *tablet/desktop* variant. This is the single most
common way to get this page wrong.

| Element | Official (phone block) | Compose | Status |
|---|---|---|---|
| `.auth-title` | 18px/26px, weight 600 | `18.sp`/`26.sp` SemiBold | ✅ match |
| `.auth-description` | margin-top 3px, 11px/17px | 3dp, `11.sp`/`17.sp` | ✅ match |
| `.auth-form` | margin-top 16px | 16dp | ✅ match |
| `.field-group + .field-group` | margin-top 12px | 12dp | ✅ match |
| `.field-label` | margin-bottom 6px, 11px, weight 500 | 6dp, `11.sp` Medium | ✅ match |
| `.field-shell` | height 42px, padding 0 11px, radius 7px | 42dp, 11dp, 7dp | ✅ match |
| `.field-icon` | 18×18, margin-right 8px | 18dp, 8dp | ✅ match |
| `.password-toggle` | 28×28 | 28dp | ✅ match |
| `.primary-button` | height 42px, radius 7px, 13px, margin-top 16px | 42dp, 7dp, `13.sp`, 16dp | ✅ match |
| `.banner-logo` | 38×38, radius 8px | 38dp, 8dp | ✅ match |
| `.banner-brand-name` | 13px/19px | `13.sp`/`19.sp` | ✅ match |
| `.banner-kicker` | 7px/10px | `7.sp` | ✅ match |
| `.banner-title` | 21px/29px, letter-spacing −0.4px | `21.sp`/`29.sp`, −0.4sp | ✅ match |
| `.banner-description` | 10px/15px | `10.sp`/`15.sp` | ✅ match |
| `.login-content` | radius 22px 22px 0 0, margin-top −18px, padding 22px 20px | 22dp, −18dp, 22dp/20dp | ✅ match |
| `--ink-focus-ring` halo | `0 0 0 3px` on focus | 3dp halo | ✅ match |
| **`.login-banner` min-height** | **200px** | was 182dp | 🔧 **fixed this pass** |
| **`.banner-inner` padding** | **top 16px, bottom 36px** | was 18dp/18dp | 🔧 **fixed this pass** |
| **`.login-content` padding-bottom** | **14px + safe-area** | was 42dp | 🔧 **fixed this pass** |
| **`.banner-copy` max-width** | **300px** | absent | 🔧 **fixed this pass** |
| **`.banner-description` margin-top** | **5px** | was 4dp | 🔧 **fixed this pass** |
| **`.primary-button[disabled]` opacity** | **.8** on the login page (new in v1.0.4) | `0.64f` | ⚠️ **stale — one-line fix** |
| `.api-route-switch` | cloud↔LAN route selector | not found | ❌ missing |
| `.legal-overlay` / terms | legal text overlay | not found | ❌ missing |
| `.social-login-*`, `.google-login-*`, `.apple-login-*` | social sign-in | n/a | ⛔ intentionally absent |
| `.verification-row` | email verification code | n/a | ⛔ intentionally absent |

The five `🔧` rows were corrected in this pass against the phone block; they are
recorded as evidence in `docs/parity/login.md`. Social sign-in and email
verification are **deliberately not replicated** — they authenticate against the
vendor's cloud, which direct-connect removes. Their absence is correct, but note
that the official page's *layout* still reserves space for them.

The `⚠️` row is the **only** parity delta v1.0.4 introduced on this page. The
release adds one rule — `.primary-button[disabled]{color:var(--ink-on-accent);
background:var(--ink-accent);opacity:.8}` — which follows the shared
`.primary-button[disabled],.secondary-button[disabled]{opacity:.64}` at equal
specificity and therefore wins on the login form: a disabled submit is
**α .80**, not α .64. `MainActivity.kt:784` still uses
`.alpha(if (busy) 0.64f …)`, so a busy login button renders ~20% too faint.
Nothing else on the page changed.

### A2. Devices (`pages/devices`)

Ground truth: `css/pages-devices.css` (147 classes). **Implemented natively since
v1.9.2** — `DevicesScreen.kt` renders the official page as `Screen.Devices`, and
the earlier project-specific `Screen.Instances` no longer exists. The per-value
audit record is `docs/parity/devices.md`. Statuses below reflect that pass.

| Official block | What it is | Compose status |
|---|---|---|
| `.device-grid`, `.device-card`, `.add-device-card` | device tiles + add tile | ✅ yes |
| `.device-symbol`/`-screen`/`-stand` | illustrated device glyph | ✅ yes |
| `.device-source-tag--local` / `--cloud` | **route provenance badge** | ✅ local on every device; `--cloud` tone exists but is unreachable |
| `.device-endpoint-tag--desktop` / `--web` | endpoint kind badge | ✅ yes |
| `.device-route-tag` | active route badge | ✅ yes |
| `.status-dot`, `.device-status--online` | presence | ✅ yes |
| `.pairing-overlay`, `.pairing-panel`, `.method-list`, `.method-option` | add-device flow | ✅ overlay + panel + manual form; scan-to-add is an entry whose step says it is not wired |
| `.api-route-switch`, `.api-route-compact-option` | **cloud vs LAN route picker** | ✅ yes — cloud option rendered disabled with the reason stated |
| `.account-popover`, `.account-entitlements` | account menu + cloud entitlement list | ✅ popover; entitlement block renders the official "unavailable" state, no fabricated plan |
| `.account-logout`, `.account-delete`, `.account-about` | account actions | ✅ logout + about; delete-account deliberately absent (cloud-only) |
| `.machine-id` | machine identifier row | ✅ yes |
| `.loading-state`, `.error-state`, `.retry-action` | page states | ✅ yes |

**The route-picker gap is now closed.** The official app ships a first-class
`api-route-switch` for choosing between the cloud relay and a LAN/direct route,
plus `device-source-tag--local`/`--cloud` badges that tell the user which route a
device is on. Both landed in v1.9.2 (`DevicesScreen.kt`). The cloud option is
rendered disabled with the reason printed under the switch, and `setApiRoute`
rejects anything other than LAN at the view-model level, so no future call site
can route traffic at the vendor's cloud by mistake.

Responsive breakpoints differ from login: devices uses `max-width: 520px` plus
two landscape queries, not `max-width: 600px`.

### A3. About, bootstrap, location-picker

| Page | Size | Status |
|---|---|---|
| `pages/about` | 2.2 KB CSS | ❌ missing (About exists only as a settings row) |
| `pages/bootstrap` | 343 B CSS | n/a — no equivalent needed |
| `pages/location-picker` | 10 KB CSS | n/a — uni-app system page, not app UI |

### A4. Login-devices and change-password — out of local-parity scope

New in v1.0.4: `pages/login-devices` (13,786 B CSS) and `pages/change-password`
(19,608 B CSS). Both are **vendor-cloud account screens** and both are
deliberately not replicated.

- `login-devices` manages the account's signed-in devices via
  `GET /api/app/auth/devices` and `DELETE /api/app/auth/devices/{id}`. Labels:
  登录设备, 已登录设备, 设备上限, 当前设备, 登录时间, 最近活跃, 旧版登录设备,
  暂无登录设备.
- `change-password` reuses the shared `ForgotPasswordForm` component (props
  `compact`, `intent`, `initialEmail`) and calls `POST /api/app/auth/password-code`
  then `POST /api/app/auth/reset-password` — an emailed 6-digit-code flow for the
  cloud account.

`app-service.js` carries 15 `/api/app/auth/*` endpoints in total and **none of
them exists in the upstream server**. Per `docs/local-parity-scope.md`
("排除或隐藏：官方云账号登录与订阅") these pages stay unimplemented; their
absence is correct, not a gap.

Do not confuse this with upstream's *local* `POST /api/auth/change-password`
(`currentPassword` + `newPassword`, used by `AccountSettings.vue`) — a different
endpoint on a different surface, and one that **is** in scope.

---

## Part B — Embedded web client surface (38 views)

The client in the APK is a **vendor build**: upstream-derived code plus a private
mobile layer. §B1 covers the upstream-derived view surface; §B2 covers the part
with no public source. Read `ref-v0.7.13` for B1 — but see *Verification limits*
§5 before assuming any tag matches what ships.

### B1. Upstream-derived views

The Compose app implements **39 screens**; the official client has **38 views**
plus 139 components. Name-level correspondence is high. **No row in this table
has been compared against v0.7.13 source yet** — status is `name` at best. This
table is a work plan, not a completion record.

| Official view | Compose screen | Surface | Verified |
|---|---|---|---|
| `LoginView` | `Login` | yes | css |
| `hermes/ChatView` | `Conversation` | yes | src (partial) |
| `hermes/GroupChatView` | `Room` | yes | name |
| `hermes/SharedGroupChatView` | `Groups` | partial | no |
| `hermes/GroupChatLinkView` | `Connections` | partial | no |
| `hermes/HistoryView` | `Chats` | partial | name |
| `hermes/DevicesView` | `Instances` / `Connections` | partial | name |
| `hermes/SettingsView`, `HermesSettingsView` | `Settings`, `SettingsGroup` | yes | name |
| `hermes/ChannelsView` | `Channels`, `Channel` | yes | name |
| `hermes/JobsView` | `CronJobs`, `CronJob`, `CronHistory` | yes | name |
| `hermes/KanbanView` | `Kanban`, `KanbanTask` | yes | name |
| `hermes/SkillsView` | `Skills`, `Skill` | yes | name |
| `hermes/SkillsUsageView` | `Insights` | partial | no |
| `hermes/PluginsView` | `Plugins` | yes | name |
| `hermes/McpManagerView` | `Mcp` | yes | name |
| `hermes/MemoryView` | `SettingsGroup.Memory` | partial | no |
| `hermes/ModelsView` | (inside `SettingsGroup.Models`) | partial | no |
| `hermes/PerformanceView` | `Insights` | partial | no |
| `hermes/PetdexView` | `Pets` | yes | name |
| `hermes/ProfilesView` | `Profiles` | yes | name |
| `hermes/ThemeView` | `Appearance` | yes | name |
| `hermes/UsageView` | `Insights` | partial | no |
| `hermes/VersionPreviewView` | `RuntimeVersions` | yes | name |
| `hermes/WorkflowView` | `Workflows` | yes | name |
| `hermes/JourneyView` | `Journey` | yes | name |
| `hermes/FilesView` | `Files` | yes | name |
| `hermes/LogsView` | `Logs` | yes | name |
| `hermes/AgentManagerView` | `AgentRuntimes`, `AgentHub` | yes | name |
| `hermes/GlobalAgentView` | `GlobalAgent` | yes | name |
| `ekko/{Settings,Memory,Skills,Mcp}View` | `EkkoHub` | yes | name |
| `hermes/DesktopBrowserView` | — | no | n/a — desktop-only surface |
| `hermes/DesktopPetView` | `Pets` | partial | no |
| `hermes/TerminalView` | — | no | n/a — desktop-only surface |
| `social-messages/SocialMessagesView` | — | no | ❌ missing |

`Webhooks` and `Journey` have no upstream view of their own — webhooks are a
settings component and Journey is `JourneyView`. `Onboarding` and `Loading` are
this project's own additions with no official counterpart.

### B2. Vendor-only mobile layer (no public source)

§B1 above maps the upstream-derived client. v1.0.4 also revealed a second
layer that has **no public source at all**, so its CSS is the only ground truth,
exactly as for the native shell pages.

`pages/index/index.css` went 522,488 → 541,505 bytes in v1.0.4: **3501 → 3635
rules, +134 added, 0 removed, 10 modified**. The new rules cluster into six
families, all mobile-only:

| Family | Classes | What it is | Compose |
|---|---|---|---|
| Mobile terminal | `.mobile-terminal-panel`, `.mobile-terminal-renderer`, ~60 `.terminal-*` (tabs, shortcut row, on-screen keyboard, font stepper, session menu, safe areas) | full-screen terminal over a `#101318` surface, with tab bar and key row | ❌ missing |
| Incoming files | `.incoming-files`, ~34 `.inbox-*` (drawer, rows, batch selection, progress, footer) | incoming-file drawer with an add action | ❌ missing |
| Conversation paddle | `.conversation-paddle`, ~21 `.paddle-*` (rail, ticks, thumb, nodes, history) | message-index rail / paging paddle overlay | ❌ missing |
| Active-health panel | `.active-health-integration`, `--layer`, `--mask`, restructured `.active-health-*` | now a panel (`max-width:680px`, `height:min(720px,88vh)`); touch targets raised 38 → 44 px | ⚠️ partial — overlay form only |
| File downloads | `.document-download*`, `.workspace-download*` (track/fill/cancel), `.workspace-file-download*` | inline download progress + cancel | ❌ missing |
| Chat empty state | `.chat-empty-agent*`, `.thinking-agent-logo`, `.thinking-avatar--logo` | agent-logo empty state in the message list | ❌ missing |

Also modified: `.task-plan-card` gains `margin-bottom: 14px`;
`.workflow-tool-button uni-image` gains `filter: var(--ink-secondary-icon-filter)`
(the same per-theme recolouring trick as `--ink-icon-filter` in A1);
`.workspace-file-copy` gains `gap: 1px`. Two icons ship with them —
`icons/download.svg`, `icons/terminal.svg`.

**These six families are in local-parity scope** (the scope doc keeps
"Terminal / Browser：本地 Studio 提供能力时显示并调用"), unlike §A4's cloud
pages. They are real, unstarted work.

That they exist only in the APK is verified, not assumed: the four scoped
components new in this bundle (`AppTerminalPanel`, `AppTerminalRenderer`,
`AppIncomingFiles`, `AppConversationPaddle`) and every class family above are
absent from **all** published upstream tags — including canonical
`EKKOLearnAI/hermes-studio` v1.0.4 (`ekko-studio` 0.7.23, the newest available;
no `mobile` package, no `ForgotPasswordForm`) and the `mrz2333/hermes-studio`
mirror (tags to v1.0.3, `main` at 0.7.19). Of the named components in the v1.0.3
and v1.0.4 bundles, 85/87 and 89/91 respectively have no public counterpart.

### B3. Scope-hash map and a class-family correction (2026-09-19)

uni-app rewrites every `data-v-<hash>` scope on each build, so a hash is not a
stable identifier — but it *is* traceable: the `@keyframes` names inside a
scoped rule carry that scope as their build suffix. Mapping the two known
Mapping the components this pass needed:

| Component | v1.0.3 scope | v1.0.4 scope | Role |
|---|---|---|---|
| `AppChatComposer` | `744a5cd2` | `a139dd89` | **the shipped mobile composer** — emits `composer-*` |
| `AppSingleChatView` | `8aca294f` | `e28f96e8` | page container — emits `input-context-*` |
| `AppMessageList` | `c0c550c3` | `45c4d322` | message list — emits `app-message-list*`, `.chat-state`, and the v1.0.4 `.chat-empty-agent*` |
| `ConversationDrawer` | `b5cd62b3` | `8390f519` | session list — emits `session-item*`, `.session-unread-dot`, `.drawer-list-state` |

The composer audit (`docs/parity/composer.md`) and the composer rows of
`docs/parity/chat.md` had these two reversed: they read the composer's card and
textarea out of `AppSingleChatView`'s scope, from `.input-wrapper` /
`.input-textarea`, and dismissed the `composer-*` family as dead. It is the other
way round. `"input-wrapper"` appears **zero** times in the 3.5 MB
`app-service.js` (and is not built dynamically either), while
`AppChatComposer` renders `class:"composer-wrapper"`, `"composer-textarea"`,
`"composer-toolbar-button"`, `"composer-safe-area"`. Those `.input-*` rules are
dead CSS left in a page container.

Both docs are corrected. What the correction changes in practice:

| | Verdict |
|---|---|
| Composer card metrics (`min-height:78px`, `padding:8px 12px 6px`, `gap:4px`, `radius:18px`, the `--ink-*` colours) | **unchanged** — `.composer-wrapper` is identical to `.input-wrapper` on every value that was checked |
| Attachment family (13 rules) | **unchanged** — `.composer-attachment*` is value-identical to `.attachment-chip*` |
| Toolbar/placeholder/toolbar-container | **unchanged** except `.toolbar-button` (35×28, dead) which has no live counterpart; the shipped buttons are a uniform 30×30 |
| `.composer-textarea` | **changed** — the live rule adds `max-height:280px; overflow-y:auto; flex:0 0 auto; box-sizing:border-box; padding:1px 0 2px; transition:height .12s ease`, i.e. a JS-height-driven self-sizing field. The port was built on `flex:1; padding:0` |

The `composer-*` family holds **56 rule selectors in both v1.0.3 and v1.0.4**;
rule-by-rule, exactly two differ and both differ only in the `@keyframes` build
suffix. So this correction does not depend on which release is the baseline.

Method note, since this is the trap that caused it: **a class name in the
stylesheet is not evidence that anything renders it.** Before treating a rule as
the spec for a surface, confirm some component emits the class — grep the class
in `app-service.js` (including `normalizeClass` / concatenated forms) and find
the owning component via `__scopeId`.

#### B3b. That check, run over `docs/parity/chat.md`

The chat record cited nine more selectors that **no component emits** — 0
occurrences in `app-service.js` in *both* releases, and in most cases no CSS rule
at all:

| Cited class | Occurrences (v1.0.3 / v1.0.4) | Live counterpart |
|---|---|---|
| `.empty-state` | 0 / 0 | `.chat-empty-agent` — v1.0.4 only |
| `.empty-logo` | 0 / 0 | `.chat-empty-agent-logo` — v1.0.4 only |
| `.session-item-unread-dot` | 0 / 0 | `.session-unread-dot` (+ `--failed`), emitted via `normalizeClass` |
| `.session-loading` | 0 / 0 | `.drawer-list-state` (+ `.drawer-list-spinner`) |
| `.session-empty` | 0 / 0 | `.drawer-list-state` (text-only branch) |
| `.session-items` (prose) | 0 / 0 | `.session-list` inside `.session-scroll` |
| `.user-message-author` | 0 / 0 | `.message-author.single-user-author` (`justify-content:flex-end`) |
| `.chat-header` | 0 / 0 | `.app-navigation*` — `AppNavigationBar`, scope `f633209f` |
| `.header-session-title` | 0 / 0 | the same bar's `.navigation-title` (15px/650), hidden by the `hideTitle` prop |

Five findings came out of this beyond the renames:
- **`session-item--unread` / `--running` / `--failed` are emitted but unstyled.**
  Only `--active` and `--pressed` have rules. They are state hooks, not a visual
  spec; the visible signals are `.session-unread-dot` (accent, 6px, with a 3px
  `--ink-selected-bg` halo — or `--ink-error` / `--ink-error-soft` when failed)
  and `.session-running-track` under the title.
- **The chat empty state is new in v1.0.4.** v1.0.3 renders an opened-but-empty
  session as a `.chat-state` line (`min-height:86px`, 12px, muted; no logo).
  v1.0.4 replaces it with `.app-message-list--empty` (a `100%` centered flex box)
  containing `.chat-empty-agent` — a centered column, `gap:12px`, holding a
  `48×48` logo at **`opacity:.4`** with a `12px` radius, plus a 14px centered
  name line. A port built against v1.0.3 should show the plain line; against
  v1.0.4, the logo state. The Compose port currently renders the logo at
  `.25f` **with no radius** — right shape, two wrong numbers.
- **The session-list loading/empty/error row is one 120px-tall centered column**
  (`.drawer-list-state`: `min-height:120px; padding:20px; gap:8px; font-size:11px;
  text-align:center`), not the 16px-padded inline note the port approximates.
- **`.message-author`'s `gap` is `4px`, not `6px`.** The port hard-codes
  `Spacer(Modifier.width(6.dp))` on both sides of the author row
  (`MainActivity.kt:2034`/`:2048`), and the comment above it at `:2019` states the
  same wrong value. `docs/parity/chat.md:222` also carried `gap:6px` (and folded
  `.message-author-name`'s colour/12px into the parent rule); both are corrected.
- **The top bar is `AppNavigationBar`, and the stricter pass is what found it.**
  The first sweep tested names with `in`, which is a *substring* test — so
  `.chat-header` passed only because `new-chat-header` contains it. The
  re-run tested **class tokens** (every `class:"…"` value, every
  `normalizeClass` array literal, every `"hover-class":"…"`) and caught
  `.chat-header` and `.header-session-title`: neither occurs as a token in either
  release, and neither has a rule. The real component is `AppNavigationBar`
  (scope `f633209f`, `.app-navigation` → 46px `.navigation-content` → centered
  `.navigation-title-wrap` with a 15px/650 `.navigation-title`), and it hides the
  title by **prop**, not by media query. The same run cleared four names the token
  test first flagged as false positives — `.composer-placeholder`,
  `.composer-action--pressed`, `.input-context-status--pressed` and
  `.session-item--pressed` are all live, emitted as quoted `"hover-class"` values
  the substring pass had no trouble with but the token pass initially missed.
  **Use the token test; a substring hit is not evidence of existence.** The test
  is scripted, so it no longer depends on getting the regex right by hand:

  ```sh
  scripts/apk/class-liveness.py --release both /root/Hermes任务文件/HStudioDirect \
      docs/parity/*.md docs/OFFICIAL_PARITY_MATRIX.md
  ```

  It reports every cited class as emitted / dead-but-acknowledged / dead-and-
  unexplained, exits non-zero on the last, and flags a class live in one release
  and dead in the other — which is what re-confirms the §B2 families below
  (mobile terminal, inbox, paddle, active-health) as **v1.0.4 additions** rather
  than classes this client always had.

`docs/parity/chat.md` is corrected at each row. Still unchecked by this method:
every selector in Part B that no parity document has audited yet. The terminal,
inbox, paddle and active-health families in §B2 are lower risk — they were taken
from the v1.0.4 stylesheet *diff*, where a class has to be new to appear — but
they are not verified emitters.

---

## Part C — Design system fidelity

The official **shell** stylesheets contain no hard-coded colours. Every colour
resolves through an `--ink-*` custom property, and most are themselves
user-overridable (`var(--app-custom-*, <default>)`). Extracted as
machine-readable tokens:

- `theme-tokens-light.json` — 50 tokens (defined on `body`)
- `theme-tokens-dark.json` — 50 tokens (defined on `.theme-dark`)

> Scope of that claim: the shell pages (`app.css`, `pages-login`, `pages-devices`,
> `pages-about`, …). The embedded client's compiled CSS mostly follows the same
> contract, but the v1.0.4 **terminal** does not — it hard-codes its palette
> (`#101318`, `#242b35`, `#8ad6b4`, …) because it is an emulator rendering its own
> colours. Do not expect an `--ink-*` route there; see §B2.

> **Corrected 2026-09-19.** The light file was previously listed as 52
> tokens. Two of its keys — `background` and `color` — are ordinary
> declarations on the `body` rule, not `--ink-*` custom properties, and
> had been swept in by a regex that did not require the prefix. Both
> token files were regenerated by `scripts/apk/extract-official-reference.py`;
> the real denominator is 50/50. The v1.0.4 palette is identical to
> v1.0.3's — no token changed between the releases.

Status: **Compose mirrors the token set** in `Theme.kt`
(`inkTextPrimary`, `inkTextMuted`, `inkLoginBg`, `inkFocusRing`, …), which is the
right architecture — it preserves user wallpaper theming. The mirror was audited
mechanically against the extracted tokens: **23 of 23 comparable tokens match**.
The audit found one real defect, now fixed:

- `inkSelectedBg(dark)` was `0x1FFFFFFF` (α 0.12); the official
  `--ink-selected-bg` dark is `rgba(255,255,255,.18)` (α 0.18). The wrong value
  is exactly `--ink-focus-ring`'s dark alpha — a copy-paste from the adjacent
  function. Dark-mode selection tints were ~33% too faint.
  `OfficialInkTokenTest` now pins the distinction.

Two caveats worth tracking:

- `--ink-icon-filter` is `none` in light but `invert(1)` in dark, i.e. the
  official app recolours icons with a CSS filter rather than swapping assets.
  A Compose port must reproduce that per-theme recolouring or icons will drift.
- 8 further tokens are overridden by the wallpaper surfaces
  (`.app-local-theme-surface--wallpaper` and its conversation/wide variants),
  which the token extraction records but Compose does not yet model.

---

## Part D — Direct-connect capability (must not regress)

This is the project's reason to exist, so it is tracked separately from visual
parity. The official app already contains the LAN capability; this project
unlocks it.

| Capability | Where | Status |
|---|---|---|
| Studio URL + account/password sign-in | `LoginScreen`, `AppViewModel.login` | ✅ present |
| Multi-instance switching | `Screen.Instances`, `Instances.kt` | ✅ present |
| REST to Studio | `HermesApi.kt` | ✅ present |
| Chat socket | `ChatSocket.kt` | ✅ present |
| Group socket | `GroupSocket.kt` | ✅ present |
| Chinese-first UI | `Locales.kt`, `values-zh-rCN/strings.xml` | ✅ present |
| Encrypted credential store | `Store.kt` | ✅ present |
| Official route picker UI | `api-route-switch` | ✅ present since v1.9.2 (cloud option disabled by design) |

---

## Verification limits

Recorded plainly, because several earlier commits in this repo describe work as
"深度复刻" (deep replication) on the strength of static comparison alone.

1. **No device or emulator verification.** No row here is confirmed by a
   screenshot. Font metrics, gesture insets, IME behaviour, and dark-mode
   transitions are all unverified.
2. **No build or unit-test run at the time of writing.** The host has JDK 8
   only, no Gradle, and an Android SDK with no `platforms/`. A toolchain was
   installed into `/opt/android-buildenv` during this pass
   (`scripts/apk/setup-build-env.sh`); results are reported in
   `docs/parity/baseline-tests.md`.
3. **Part B is name-level only.** Treat every `name` row as unstarted work.
4. **The chat screen is the largest unverified area.** It was partly compared
   against v0.7.18 source. `ref-v0.7.13` is pinned locally, so re-auditing the
   upstream-derived surface against a single chosen tag is unblocked.
5. **There is no tag that matches the shipped client.** The bundle embeds a
   vendor build: upstream code plus a private mobile layer. The only version
   literal in `app-service.js` is `0.7.13`, and it is the *minimum Studio server
   version*, not the client version (error key `studio_version_unsupported`).
   Earlier revisions of this document and of `docs/reference/apk-1.0.3/README.md`
   read it as the client version; that was wrong. For the mobile-only surfaces in
   §B2 the APK stylesheet is the only ground truth.
6. **The v1.0.4 shell pages are otherwise unchanged.** `app.css` is
   byte-identical to v1.0.3 and `about`/`bootstrap`/`location-picker` are
   identical after stripping the per-build scope hashes; `devices` has no rule
   changes at all. Compare with care: every build rescopes selectors
   (`[data-v-<hash>]`) *and* suffixes `@keyframes` names with 8 hex digits, so a
   raw diff invents changes. Normalise both before concluding anything.

### The evidence index is still pinned to v1.0.3

`docs/original-app-ui-evidence.json` (24 domains, 88 scopes, 27 CSS anchors)
pins `latest/HStudio-v1.0.3.apk` and `decoded-v103/`, and
`scripts/verify-original-app-evidence.py` re-derives its spans from that tree and
validates its sha256s. It **still verifies** (27/27 CSS anchors resolve, 24/24
domains) and is unchanged, so it remains valid *v1.0.3* evidence.

It has not been regenerated for v1.0.4, and that is not a mechanical edit: every
`data-v-<hash>` scope changed with the build, and `pages/index/index.css` grew by
134 rules, so all 27 recorded CSS offsets shifted. Regenerating it means
(i) remapping each domain's scope list old→new **by component name** (names are
stable across builds; hashes are not — the script already extracts `__name`), and
(ii) recomputing every CSS anchor offset against the v1.0.4 stylesheet. Until
that is done, read the index as v1.0.3 evidence and take v1.0.4 structure from
`docs/reference/apk-1.0.4/`.

---

## Highest-value next steps

1. ~~Implement the devices page (`pages/devices`) natively, including
   `api-route-switch` and the `device-source-tag--local/--cloud` badges.~~
   **Done in v1.9.2** — see §A2 and `docs/parity/devices.md`.
2. **Fix the login-button alpha** (§A1) — `MainActivity.kt:784` `0.64f` → `0.8f`.
   One line, ground truth is unambiguous, and it is the only shell-page delta
   v1.0.4 introduced.
3. **Build the mobile terminal** (§B2). It is in local-parity scope, it is the
   largest single new surface in v1.0.4 (~60 classes plus a `#101318` dark
   palette that does not follow `--ink-*`), and upstream cannot help — the
   stylesheet is the whole specification.
4. Re-audit `Conversation`/`Chats` against `ref-v0.7.13`, and record in
   `docs/parity/chat.md`. Still the largest unverified area. Note the §B3 lesson
   first: `chat.md`'s composer rows had been read from a scope whose classes
   nothing renders, so re-check *class liveness in `app-service.js`* before
   trusting any row taken from the stylesheet alone. §B3b has now run that check
   over the whole file and corrected seven rows; the Compose deltas it surfaced
   are still unfixed in `MainActivity.kt` — `.chat-empty-agent-logo` should be
   `opacity:.4` clipped to `12dp` (the port has `.25f`, no radius), the
   session-list state row should be `.drawer-list-state` (120px centered column,
   `gap:8px`, 11sp), not a 16px-padded note, and the message author row's gap is
   `4px`, not the port's `6.dp` spacer.
5. Finish the Composer audit — **re-scoped 2026-09-19** (§B3). The spec is the
   `composer-*` family on `AppChatComposer`, not the dead `.input-*` rules the
   earlier pass read; `docs/parity/composer.md` now carries the full 56-rule spec.
   Remaining: pair each sub-element with its Compose node (`.composer-toolbar`,
   `.composer-toolbar-button`, the three 30×30 action buttons,
   `.composer-resize-handle`, `.composer-status-bar`, `.composer-safe-area`), and
   assert the card's unasserted `min-height: 78px` — which is what stops the
   composer collapsing to a single text line. `.composer-textarea`'s
   `max-height: 280px` cap and drag-resize transition are the newest known gaps.
6. Incoming files (§B2) and the conversation paddle (§B2) — both new in v1.0.4,
   both in scope, both unstarted.
7. Then Settings/Appearance, which are mostly token-driven and cheap.
8. `pages/about` is still unimplemented (§A3) and
   `social-messages/SocialMessagesView` has no Compose surface at all.
9. Everything else in Part B is still `name`-level: each row needs a real
   `ref-v0.7.13` source comparison before it can be called replicated. Add a
   scope-liveness check to that pass — for each official scope cited, resolve
   `__scopeId` to its `__name` and confirm the component is actually instantiated
   (§B3).
10. §A4's cloud pages (`login-devices`, `change-password`) are intentionally
    out of scope — no action.

## Build/test evidence (updated 2026-09-19)

`docs/parity/baseline-tests.md` records that **this host** cannot build Android
(aarch64 host; AGP resolves an x86-64-only `aapt2`, and Debian's ARM64 `aapt2`
is build-tools 29.0.3 and cannot parse the android-35 resource table). That is
still true.

CI is the authoritative verification path. Every push to `main` runs
`testDebugUnitTest` + `lintDebug` + `assembleRelease` on x86-64 `ubuntu-latest`
and publishes a signed APK. Android CI on `2d7d72f` (v1.9.5) is **green**, so the
Kotlin compiles and the 12 unit tests pass. Claims in `docs/parity/*.md` that say
"not compiled" / "not run" describe the *host*, not CI — read them with that in
mind. No row anywhere in this document is yet verified by a device screenshot
except the v1.9.2 devices pass captured in `releases/v1.9.2-*.png`.
