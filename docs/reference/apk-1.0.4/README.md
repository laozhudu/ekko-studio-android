# Official HStudio APK reference (v1.0.4)

Ground-truth assets extracted from the **official, vendor-signed** HStudio Android
app. Everything under this directory is evidence for the parity work in
`docs/OFFICIAL_PARITY_MATRIX.md`; it is read-only reference material, never
compiled into the app.

**v1.0.4 is the current parity baseline** (it superseded v1.0.3 on 2026-09-18).
See `docs/reference/apk-1.0.3/README.md` for the previous baseline.

## Baseline provenance

The official app is a **uni-app** bundle. Its native shell pages are closed
source, so the compiled CSS/JS inside the APK is the *only* authoritative
specification for them. Do not substitute the community Compose client or a
rebuilt APK for it.

| | |
|---|---|
| File | `HStudioDirect/official-v1.0.4/EkkoStudio-v1.0.4.apk` |
| SHA-256 | `af47a968df4863190b299964bd6cca5eb9b850b26253a817a593f85c5c6fe5c7` |
| Size | 32,029,513 bytes |
| Package | `com.ekkostudio.ai` |
| versionName / versionCode | `1.0.4` / `123` |
| App label | `Ekko Studio` |
| Signer | `C=CN, ST=Shanghai, O=EKKO Learn AI, OU=Mobile, CN=Hermes Studio` (cert serial `86f92d5a45b72de6` — same certificate as v1.0.3) |
| uni-app id | `__UNI__1F41684` |
| minSdk / targetSdk | 21 / 36 |
| New in manifest | `Push` permission (v1.0.3 has none) |

The signer certificate is byte-identical to v1.0.3's, so this is a genuine
vendor release from the same signing identity, not a rebuild.

This is *not* the same file as `HStudio直连App/HStudio-Direct-v1.9.1.apk`
(`d1c9fc50…`), which is this project's own patched build and must never be used
as a visual baseline.

## What actually changed from 1.0.3

Extracted with `scripts/apk/extract-official-reference.py`; the comparison below
is of that extraction, which is reproducible (see *Reproducing the extraction*).

### Native shell pages

| Page | Change |
|---|---|
| `pages/login` | **+1 rule**: `.auth-form .primary-button[disabled]` → `color: var(--ink-on-accent); background: var(--ink-accent); opacity: .8` |
| `pages/devices` | none (only the per-build `[data-v-*]` scope hashes and animation-name suffixes differ) |
| `pages/about` | none |
| `pages/bootstrap` | none |
| `pages/location-picker` | none |
| `app.css` | **byte-identical** to v1.0.3 (`d5ce5e550dd17391`) |

So the shell UI is unchanged apart from one disabled-state rule on login. The
Compose parity conclusions recorded for A1/A2/A3 in `docs/OFFICIAL_PARITY_MATRIX.md`
therefore still hold unchanged for v1.0.4.

### Two new native pages — both vendor-cloud account screens

The bundle's route list grows from 6 to 8 (`app-config-service.js`):

```
pages/about/index  pages/bootstrap/index  pages/change-password/index
pages/devices/index  pages/index/index  pages/location-picker/index
pages/login-devices/index  pages/login/index
```

| Page | CSS | What it is |
|---|---|---|
| `pages/login-devices` | 13,786 B | Signed-in-device manager. Calls `GET /api/app/auth/devices` and `DELETE /api/app/auth/devices/${encodeURIComponent(id)}`. Labels: 登录设备, 已登录设备, 设备上限, 当前设备, 登录时间, 最近活跃, 旧版登录设备, 暂无登录设备, 新设备登录超出上限时，将自动退出最早登录的设备。 |
| `pages/change-password` | 19,608 B | Password change for the **cloud account**. Reuses the shared `ForgotPasswordForm` component (props `compact`, `intent` = `"reset"`, `initialEmail`), which calls `POST /api/app/auth/password-code` then `POST /api/app/auth/reset-password`. Labels: 修改密码, 设置密码, 请输入 6 位数字验证码, 确认新密码, 修改密码后，所有设备都需要重新登录。, 密码已修改，请重新登录, 当前账号没有绑定邮箱，暂时无法修改密码。 |

**Both are cloud-account screens and are deliberately out of local-parity scope.**
`app-service.js` contains 15 `/api/app/auth/*` endpoints (`login`, `register`,
`logout`, `me`, `refresh`, `account`, `entitlement`, `config`, `email-code`,
`password-code`, `reset-password`, `devices`, `google`, `apple`, `github`) and
none of them exists in `packages/server` of the upstream repo. Per
`docs/local-parity-scope.md`, official cloud account login and subscription are
excluded from this project, so `login-devices` and `change-password` get **no
Compose equivalent** — their absence is correct, not a gap to close.

Note the near-miss: upstream *does* have a local `POST /api/auth/change-password`
(`currentPassword` + `newPassword`, used by `AccountSettings.vue`). That is a
different endpoint on a different surface; it is not what this page calls.

### Embedded web client

`pages/index/index.css` grows 522,488 → 541,505 bytes: **3501 → 3635 rules,
+134 added, 0 removed, 10 modified**. The added classes cluster into six
feature families, all mobile-only:

| Family | Classes | What it is |
|---|---|---|
| Mobile terminal | `.mobile-terminal-panel`, `.mobile-terminal-renderer`, ~60 `.terminal-*` (tabs, shortcuts, keyboard, font stepper, session menu, safe areas) | Full-screen terminal with tab bar and an on-screen key row, on a `#101318` background |
| Incoming files | `.incoming-files`, ~34 `.inbox-*` (drawer, rows, selection, progress, footer) | Incoming-file drawer with batch selection and an "add" action |
| Conversation paddle | `.conversation-paddle`, ~21 `.paddle-*` (rail, ticks, thumb, nodes, history) | Message-index rail / paging paddle overlay |
| Active-health panel | `.active-health-integration`, `--layer`, `--mask` + restructured `.active-health-*` | Now a full-width panel (`max-width: 680px`, `height: min(720px, 88vh)`); touch targets raised 38 → 44 px |
| File downloads | `.document-download*`, `.workspace-download*` (progress track/fill/cancel), `.workspace-file-download*` | Inline download progress for documents and workspace files |
| Chat empty state | `.chat-empty-agent*`, `.thinking-agent-logo`, `.thinking-avatar--logo` | Agent-logo empty state in the message list |

Smaller modified rules worth noting: `.task-plan-card` gains
`margin-bottom: 14px`, `.workflow-tool-button uni-image` gains
`filter: var(--ink-secondary-icon-filter)` (the same per-theme icon recolouring
as A1's `--ink-icon-filter`), `.workspace-file-copy` gains `gap: 1px`, and the
bundle now resets `uni-button` globally.

Two new icons ship with them: `icons/download.svg`, `icons/terminal.svg`
(80 → 82 icons).

## The embedded client's version is *not* v0.7.13

The v1.0.3 README (and `docs/OFFICIAL_PARITY_MATRIX.md` before this pass) stated
that the APK embeds web client **v0.7.13**. That is wrong, and this release is
what proves it.

The literal `0.7.13` occurs 21 times in `app-service.js`, and all 21 are the same
thing — a **minimum Studio server version**, not a client version. Twenty are the
locale variants of one i18n message; the twenty-first is a constant:

```
"studio_version_unsupported"
"当前 Studio 版本过低，请升级到 0.7.13 或以上版本后再连接。"
const _l = "0.7.13";
```

The constant is not a client version either. It is the default argument of the
semver comparator, and it feeds the error above:

```js
class Vl extends Error { constructor(e){ super("studio_version_unsupported")
  this.name = "UnsupportedStudioVersionError"; this.actualVersion = String(e||"").trim() } }
function Bl(e, t = _l){ /* compare e against _l */ }
```

`0.7.13` is therefore the **minimum Studio server version the client will connect
to**. The bundle carries no self-identifying client version. (v1.0.3 has the
identical structure with `const Ar="0.7.13"` — the minified name is the only
difference, which is itself evidence the gate did not move between releases.)

The mobile-only layer settles the question for v1.0.4. The four scoped components
new in this bundle (`AppTerminalPanel`, `AppTerminalRenderer`,
`AppIncomingFiles`, `AppConversationPaddle`) and the class families above exist in
**no published upstream tag**, including `v1.0.4` of the canonical repo
`EKKOLearnAI/hermes-studio` (= `ekko-studio` 0.7.23, the newest available — it
contains no `mobile` package and none of `mobile-terminal-panel`,
`incoming-files`, `active-health-mask`, `conversation-paddle`, `inbox-drawer`,
`workspace-download`, `ForgotPasswordForm`). The same held for the local mirror
`mrz2333/hermes-studio` (tags up to v1.0.3, `main` at 0.7.19).

So: the embedded client is a **vendor build** — upstream-derived, plus a private
mobile layer — and no public tag can be used as its source of truth. For the
chat/terminal/inbox surface, the APK's compiled CSS is the ground truth, exactly
as it is for the native shell pages. `ref-v0.7.13` remains useful for the
upstream-derived majority, but it must not be treated as "the version the app
ships".

## Contents

| Path | What it is |
|---|---|
| `theme-tokens-light.json` | The 50 `--ink-*` custom properties defined on `body` in `app.css` — the entire light palette |
| `theme-tokens-dark.json` | The 50 `--ink-*` properties defined on `.theme-dark` |
| `css/app.css` | Base stylesheet: theme tokens, Quill + highlight.js vendor styles (identical to v1.0.3) |
| `css/pages-login.css` | Login page — includes the `@media (max-width:600px)` phone block |
| `css/pages-devices.css` | Device list / add-device page |
| `css/pages-index.css` | The embedded web client's compiled CSS (541 KB) — the only spec for the mobile terminal/inbox/paddle surfaces |
| `css/pages-login-devices.css`, `css/pages-change-password.css` | New cloud-account pages (out of scope, kept as evidence) |
| `css/pages-about.css`, `css/pages-bootstrap.css`, `css/pages-location-picker.css` | Remaining uni-app shell pages |
| `icons/` | 82 official SVG/PNG icons from `static/icons` |
| `agents/` | 7 agent-family logos (claude, codex, deepseek, ekko, grok, opencode, pi) |
| `uni-manifest.json` | Decoded `manifest.json` from the bundle |

## Reading the CSS

uni-app scopes every selector with `[data-v-<hash>]`, and the hash changes on
every build. Strip it before comparing anything:

```sh
scripts/apk/css-rules.py docs/reference/apk-1.0.4/css/pages-login.css field-shell
scripts/apk/css-rules.py docs/reference/apk-1.0.4/css/app.css --props --ink-text-primary
```

The same applies to `@keyframes` names, which carry an 8-hex-digit build suffix
(`auth-panel-in-e7341aaa` vs `auth-panel-in-d62010f4`). A raw `diff` of two
releases therefore reports changes that are not changes; normalise both
`\[data-v-[0-9a-f]+\]` and `-[0-9a-f]{8}\b` first.

### A rule is not a rendered element

`css/pages-index.css` contains every rule the build ever compiled, including
leftovers of components that were renamed or replaced — `.input-wrapper` and
`.input-textarea` have rules in both releases and **nothing emits them**. Before
citing a selector as the spec for a surface, check that some component actually
puts it on an element:

```sh
scripts/apk/class-liveness.py --release both /root/Hermes任务文件/HStudioDirect \
    docs/parity/*.md docs/OFFICIAL_PARITY_MATRIX.md
```

It is a **class-token** test, not a substring test — `new-chat-header` contains
`chat-header`, so `grep` will happily confirm a class that does not exist. It
also resolves dynamically built names (`"device-source-tag--" + (… ? "cloud" :
"local")`, `` `device-endpoint-tag--${at(o)}` ``) and treats a name live in one
release but not the other as a finding, not a pass.

### The `--ink-*` token contract

Colours are **never hard-coded** in the official shell stylesheets. Every one
resolves through an `--ink-*` property, and most of those are themselves
overridable by a user wallpaper theme (`var(--app-custom-*, <default>)`). A parity
implementation that inlines a hex value instead of routing through the token
loses the custom theme support the official app has — see `Theme.kt` for the
Compose mirror.

The compiled web client is the exception: its terminal panel uses literal dark
colours (`#101318`, `#242b35`, `#8ad6b4`, …), because it is a terminal emulator
rendering its own palette rather than a themed surface.

## Reproducing the extraction

Both the decode and the extraction are scripted, so this directory can be
regenerated and audited:

```sh
# 1. decode the official APK (needs Java 17)
java -jar work/apktool.jar d -f -o latest/decoded-v104 \
    official-v1.0.4/EkkoStudio-v1.0.4.apk

# 2. extract the reference assets
scripts/apk/extract-official-reference.py \
    latest/decoded-v104 docs/reference/apk-1.0.4
```

The script reads only `assets/apps/__UNI__1F41684/www` and writes only into the
output directory. Running it against `latest/decoded-v103` reproduces
`docs/reference/apk-1.0.3/` byte-for-byte (98 files), which is how the token
extraction was validated.

`app-service.js` (3.5 MB, up from 3.3 MB) holds every page's compiled Vue render
function; it is the source for structural questions the CSS cannot answer
(endpoints, i18n strings, component props).
