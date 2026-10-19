# `pages/devices` parity record

Implementation: `android/app/src/main/java/xyz/rflg/hstudiodirect/DevicesScreen.kt`
(state in `AppViewModel.kt` → `Screen.Devices` / `DevicesUi`, model in `Instances.kt`).

Ground truth, in this order:

| Source | What it settles |
|---|---|
| `docs/reference/apk-1.0.3/css/pages-devices.css` | every size, colour, radius and breakpoint (187 rules, scope `data-v-61a2f361`) |
| `app-service.js` in the APK bundle (offsets below) | what the page *renders*: card contents, badges, panels, labels |
| `docs/OFFICIAL_PARITY_MATRIX.md` §A2 | the gap list this pass was scoped against |

Offsets into `assets/apps/__UNI__1F41684/www/app-service.js` (APK v1.0.3), for
re-checking any claim below:

| Offset | Content |
|---|---|
| `1146` | `ApiRouteSwitch` option table: `[{cloudflare, Cloudflare}, {official, 官方直连}]`, default `official` |
| `29425`–`31000` | `ApiRouteSwitch` component (`.api-route-compact` and `.api-route-switch`), scope `data-v-ac60d9fd` |
| `121500`–`132000` | devices setup: add/rename/delete/route/entitlement handlers |
| `139000`–`147600` | devices template: header, account popover, entitlement rows |
| `151000`–`159500` | devices template: grid, device card, add card, method panel |
| `159500`–`166000` | devices template: manual pairing form, rename panel, route panel |

## What the official page is

Not "the multi-instance list". It is a **device grid** for the account's devices,
where each device carries the *route* it is reached by, plus an add-device
pairing flow and an account popover. The Compose app previously rendered this
area as `Screen.Instances` — a plain saved-server list — which is a different
screen that happened to answer a similar question. That screen is now this one;
`Screen.Instances` no longer exists.

## Structure → Compose

| Official | Compose | Evidence |
|---|---|---|
| `.devices-page` (100vh, column, `--ink-bg-primary`) | root `Box`/`Column` | css |
| `.devices-header` + `.devices-header-content` (max-width 680, `calc(18px+safe-top) 18px 18px`) | `DevicesHeader` | css |
| `.section-title` 22/29 w650 −0.25px, `.section-description` 12/18 | header texts | css |
| `.section-actions`, `.device-count` (h24, r12, 11px) | count chip | css |
| `.account-trigger` 34dp circle | `AccountTrigger` | css |
| `.account-popover` (260dp, top 43px, r13, p8) | `AccountPopover` | css |
| `.account-summary` / `-avatar` 34dp / `-name` 12/17 w620 / `-detail` 9/14 | popover header | css |
| `.account-entitlements` (p10 8) | 账号权益 block | css |
| `.account-about`, `.account-logout` (h36, p0 9, r8, 12px, icon slot 24×18, icon 16) | `AccountAction` | css |
| `.devices-scroll` / `.devices-content` | scrolling column | css |
| `.device-grid` (2 cols, gap 12) | `DeviceGrid` (manual `chunked` rows) | css |
| `.device-card`, `.add-device-card` (min-h 170, p17, r17) | `DeviceCard`, `AddDeviceCard` | css |
| `.device-card--pressed` (scale .985), `--connecting` (opacity .72, no pointer) | `clickable` + `alpha` | css |
| `.device-symbol` / `-screen` 31×22 r5 / `-screen-dot` 2×2 @3,3 / `-stand` 11×6 + 1.5×5 stem | `DeviceSymbol` | css |
| `.device-status` (p4 7, r10, 10/12) + `.status-dot` 5×5 | `DeviceStatusPill` | css |
| `.device-status--online` `#397454` / dark `#8fc9a5` | same | css |
| `.device-card-copy` (mt18, gap4), `.device-name` 14/20 w620 clamp-1, `.device-system` 10/15 | card copy | css |
| `.device-version-row` (mt11, gap6, wrap) + chip 8/12 mono p3 6 r6 | `FlowRow` + `DeviceTag` | css |
| `.device-source-tag--local` `#397454` / dark `#8fc9a5` | `DeviceTagTone.Local` | css |
| `.device-source-tag--cloud` `#7657a6` / dark `#bea7e3` | `DeviceTagTone.Cloud` (kept, unreachable — see below) | css |
| `.device-endpoint-tag--desktop` `#397454` / `--web` `#3f648f` (dark `#8fc9a5` / `#99bde6`) | `DeviceTagTone.Desktop/Web` | css |
| `.device-route-tag` (`--ink-text-primary`, w600) | `DeviceTagTone.Route` | css |
| `.device-card-footer` (mt auto, pt12, 9/13), `.machine-id` (mono, right, ellipsis, max-w 70%) | footer row | css |
| `.device-card-actions` (mt11, pt10, gap7, top border) + `.device-card-action` (h28, flex 1, r8, 10px w550) | action row | css |
| `.device-card-action--danger` `--ink-error`, `--disabled` opacity .58 | delete action | css |
| `.add-icon` 38dp circle, `＋` 24px w300; `.add-title` 13/18 w600; `.add-description` 10/15 | `AddDeviceCard` | css |
| `.loading-state` (min-h 170, spinner 18) + `.loading-spinner` | `LoadingState` + `Spinner` | css |
| `.device-grid > .error-state` (spans grid, row, p10 12, r10) + `.retry-action` | `ErrorBanner` | css |
| `.pairing-overlay` (fixed, bottom-aligned, p16, rgba(0,0,0,.42)) | `PairingPanel` | css |
| `.pairing-panel` (max-w 440, p20, r20) | `PairingPanel` | css |
| `.pairing-header/-title` 18/24 w650 / `-description` 11/17 / `-close` 26dp | `PairingPanel` header | css |
| `.method-list` (mt18, gap10) + `.method-option` (min-h 68, p11 12, r14) + `.method-icon` 38dp r11 | `PairingMethodsPanel`, `MethodOption` | css |
| `.pairing-form` (mt20, gap14), `.pairing-field` gap6, `.pairing-label` 11/16 w550, `.pairing-input` h44 p0 13 r12, `.connection-input` mono 12 | `PairingField` | css |
| `.pairing-error` 10/14 `--ink-error` | inline field error | css |
| `.pairing-submit` (h44, r13, 13px w600), `[disabled]` .58, `.button-spinner` 14 | submit row | css |
| `.api-route-switch` (p13, r12) + `.api-route-heading/-title/-description` + `.api-route-options` (2-col grid, p3, gap3) + `.api-route-option` (min-h 38) | `ApiRouteSwitch` | css |
| `.api-route-compact` (p2, r8, gap2) + `.api-route-compact-option` (9px w600) | `ApiRouteSwitch(compact = true)` | css |

Text (from the render function, `$t()` keys → the zh values the app ships):

| Official key | Value used |
|---|---|
| `.section-title` | 我的设备 |
| `.section-description` | 管理你的 Ekko Studio |
| `.device-card` status | 连接中 / 在线 / 检测中 / 离线 |
| `.device-source-tag--local` | 局域网 |
| `.device-endpoint-tag--desktop` / `--web` | 桌面端 / Web 端 |
| `.device-version-row` last chip | `Studio <hermes_web_ui_version>`, `未知` when absent |
| `.device-card-footer` | 当前在线 / 暂未连接 / 刚刚在线 / N 分钟前在线 / N 小时前在线 / N 天前在线 |
| `.device-card-action` | 修改名称 / 删除 (删除中 while in flight) |
| `.add-device-card` | ＋ / 添加设备 / 扫码或输入设备连接 |
| `.loading-state` | 正在读取设备 |
| `.retry-action` | 重新同步 |
| method list | 添加设备 / 选择一种设备连接方式。/ 扫码添加 / 手动添加 |
| manual form | 设备连接 / 设备名称 / Studio 账号 / Studio 密码 → 登录并添加设备 |
| rename panel | 修改设备名称 / 保存名称 |
| account popover | 账号权益 / 权益状态暂不可用 / 关于 Ekko Studio / 退出登录 |

## Ported logic (not just layout)

| Official helper | Port | Where |
|---|---|---|
| `it(name)` — name clamped to 40 chars | `deviceDisplayName` | `Instances.kt` |
| `Xe(device)` — `metadata.os` → Apple/Windows/Linux, else product name | `deviceSystemLabel` | `Instances.kt` |
| `nt(device)` / `at(device)` — `endpoint_kind` → desktop/web | `deviceEndpointKind` | `Instances.kt` |
| `rt(device)` — last-seen buckets | `deviceAge` / `deviceAgeCount` | `Instances.kt` |
| `Aa(connection)` — origin extraction (`connectionUrl=`/`url=` in a pasted payload, scheme+host+port, port range check) | `normalizeDeviceConnection` | `Instances.kt` |
| `ve(device)` — probe in flight → 检测中 | `DevicesUi.isChecking` | `AppViewModel.kt` |
| `fe(device)` — card opening → 连接中 | `DevicesUi.connecting` | `AppViewModel.kt` |
| `Ee(device)` — removal in flight → 删除中 | `DevicesUi.deleting` | `AppViewModel.kt` |
| `pe()` / `ge(device)` — per-device reachability pass | `probeDevices()` | `AppViewModel.kt` |
| `he(device)` — open a device | `connectDevice()` | `AppViewModel.kt` |
| `La()`/`ua()` local device store | `Store.instances` (encrypted) | `Store.kt` |
| `ApiRouteSwitch` storage read/write, default `official` | `Store.apiRoute`, default `ApiRoute.LAN` | `Store.kt` |
| cloud status / entitlement fetch (`Va()`, `Ea()`) | **not ported** — cloud only | — |

## Route handling — the one deliberate divergence

The official picker's options are `cloudflare` and `official`: **both are the
vendor's cloud**, and the app defaults to `official`. This build has no cloud
account, so it offers:

- `局域网直连` (Direct LAN) — selected, persisted, and the only value
  `setApiRoute` will accept. It rejects anything else at the view-model level,
  not merely in the UI, so no future call site can route traffic at the vendor's
  cloud by mistake.
- `云端中继` (Cloud relay) — rendered **disabled**, with the reason printed under
  the switch: 本版本未接入云端中继，应用只通过局域网直连。

No entitlement row, plan name, or "有效至" date is fabricated. The popover's
账号权益 block renders the official `.account-entitlements-unavailable` state
("权益状态暂不可用"), which is the page's own way of saying "no entitlement
data" — the same branch the official app shows when its entitlement fetch fails.

## Implemented

- Official grid, card, symbol, badges, status pill, footer, actions, add tile.
- `device-source-tag--local` on every device (a direct device *is* local); the
  `--cloud` tone exists in `DeviceTag` and is pinned by test but is unreachable.
- `device-endpoint-tag--desktop/--web` — see the inference note below.
- `device-route-tag` shown for the LAN route.
- States: loading (正在读取设备 + spinner), in-grid error + 重新同步, per-card
  检测中 → 在线/离线, 连接中 on open.
- Pairing: method list → manual form (设备连接 / 设备名称 / Studio 账号 /
  Studio 密码 → 登录并添加设备), rename panel, route panel.
- Account popover: summary, entitlement-unavailable block, route row, 关于
  (→ `SettingsGroup.About`, back returns to devices), 退出登录.
- Responsive: the three official breakpoints (≤520 → 1 column and 156dp cards;
  short landscape → 12dp gutters, 19px title, 0 min-height, 26dp actions; wide
  landscape → 1240dp max-width, auto-fit ≥300dp columns).
- Direct-connect behaviour preserved: encrypted multi-instance store, the
  existing REST/WebSocket clients, Chinese-first strings, and sign-in.

## Partially implemented

- **Scan-to-add** (`.method-option` 扫码添加) is present as an entry with the
  official title and description, but the step behind it says so and offers the
  manual field instead. The official method list is part of the page's shape, so
  removing the option would misrepresent it; wiring a camera scanner is not in
  this pass. `uni.scanCode` has no Compose equivalent here.
- **`.device-route-tag` for cloud devices.** Upstream prints it only in the
  `cloud` branch. Every device here is local, so the tag is shown for the LAN
  route — that is the "active route" reading the parity matrix uses, and the
  alternative is a card with no route at all.
- **`删除中` / `正在保存`.** The branches exist and are driven, but removal and
  rename are synchronous local writes, so they are rarely observable.
- **Per-device presence.** The official app asks a cloud status endpoint. Here
  each saved device is probed directly (`GET /api/auth/me` +
  `GET /api/hermes/runtime-versions` with its own token, on a throwaway client so
  probing never re-points the shared clients). That is a real observation of the
  device, but it is a *different* signal from the official one: a device that is
  on another network will read 离线 even if the vendor cloud would have seen it.
- **Avatar.** `.account-avatar-image` needs an account avatar from Studio; the
  popover currently always renders the `.account-avatar-fallback` initial.

## Not implemented (deliberate)

| Official | Why |
|---|---|
| `.account-web-login` (扫码登录官网) | authenticates against the vendor cloud; there is no account to log in to |
| `.account-delete` (删除账号 + double confirm) | permanently deletes a vendor cloud account; this app has no such account to delete. Removing a *device* is the card's 删除 action instead |
| `.account-entitlement-row` ×2 | cloud subscription state; showing it would mean inventing plan data |
| `.account-entitlements-syncing` pulse | only meaningful while fetching cloud entitlements |
| Cloud branch of the card actions (切换 App 线路) | cloud-only affordance |
| `pages/about` navigation | that page is still unimplemented (`OFFICIAL_PARITY_MATRIX.md` §A3); 关于 opens the existing About settings group instead |

## Inferences (not extractions)

Stated separately because they are the claims a reviewer should attack first:

1. **`endpoint_kind = web` for a saved direct Studio** (`DEFAULT_ENDPOINT_KIND`).
   The official app reads this from `metadata.endpoint_kind`, which a direct
   connection never receives. A direct connection terminates at the Studio HTTP
   surface, so `web` is what is stored; a Studio that announces a desktop
   endpoint can override it, since the value is per-device and persisted.
2. **`platform` from `GET /api/hermes/runtime-versions`.** The official app reads
   `metadata.os`; the runtime-versions response carries the equivalent platform
   string, and it is mapped through the official matcher. If the endpoint is
   unavailable the `.device-system` line falls back to the official product-name
   default rather than inventing an OS.
3. **Bare `host:port` accepted in the connection field.** The official regex
   requires a scheme. Rejecting a hand-typed LAN address would break the primary
   path this app exists for; the scheme is guessed (http for an IP/`.local`/
   explicit port, https otherwise). The port range check and the scheme
   preservation are the official ones.

   One behaviour was checked against the compiled `Aa()` rather than assumed: a
   raw JSON payload (`{"connectionUrl":"http%3A%2F%2F…"}`) is **not** unwrapped,
   because the official separator class is `[?&#]` and has no `"`. Only
   querystring-shaped payloads — what the Studio QR code actually carries — are
   unwrapped. `DevicesParityTest` pins the null result so the port cannot
   silently drift from the original.
4. **A back button in the header.** The official page has none — uni-app's page
   stack owns back. This screen is pushed inside a single activity, so it needs
   its own affordance; it is placed in the heading row and sized to the account
   trigger.

## Verification

| Check | Result |
|---|---|
| Official CSS values vs `DevicesScreen.kt` | transcribed rule by rule; the values are pinned by `DevicesParityTest` |
| Render-function structure (`app-service.js`) | read directly, offsets recorded above |
| Token usage (`--ink-*`) | all colours route through `Theme.kt` helpers; the only literals are the four badge/status greens/blues the official stylesheet also hard-codes |
| Kotlin compile | **not run** — see below |
| Unit tests | **not run** — same blocker |
| Device/emulator | **not available** |

### Build and test limits

Unchanged from `docs/parity/baseline-tests.md`: this host is aarch64, and AGP
8.9.2 resolves `aapt2-8.9.2-12782657-linux`, which is x86-64 only. Overriding
with the system ARM64 aapt2 (Debian build-tools 29.0.3) cannot parse the
android-35 resource table. `processDebugResources` is a prerequisite of
`compileDebugKotlin` and of every unit test, so **nothing in this repo has been
compiled or executed on this host**, including this pass.

What was done instead, and what it does and does not prove:

- `git diff --check` — whitespace only.
- A bracket/string-balance parse of every edited Kotlin file (no Kotlin compiler
  is installed and the Gradle cache holds no Compose or Kotlin-compiler
  artifacts) — proves the files are structurally parseable, **not** that they
  type-check.
- Reference audit: every `viewModel.*` call, every `state.*` field and every
  `R.string.*` used by `DevicesScreen.kt` was mechanically matched against
  `AppViewModel.kt` / `UiState` / `res/values/strings.xml`; all resolve, and the
  new strings exist in all three locales with identical placeholders (checked
  with the same rules `TranslationsTest` applies).
- `DevicesParityTest` (added this pass) pins the official numbers and the ported
  helper behaviour as source assertions, in the same spirit as
  `OfficialInkTokenTest` — it cannot run here either, but it fails on the next
  x86-64 CI run if a later edit drifts from the APK.

**No claim in this document is backed by a screenshot or a running build.**
