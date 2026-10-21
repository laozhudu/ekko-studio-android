# Behaviour parity with the official APK 1.0.8

Source of evidence: the ten page bundles in
`assets/apps/__UNI__1F41684/www/pages/` plus the shared UI copy inside
`app-service.js`. Only *behaviour* was taken from them — no markup, stylesheet,
string table or artwork entered this repository. Copy is paraphrased in the
table below, never reproduced wholesale.

The question this answers: what does the official client actually do, and where
does this app disagree?

## Status legend

- **have** — implemented, and a test or probe backs it.
- **have, differently** — implemented with a different shape; noted.
- **adopted** — missing before this pass, added in 0.16.0.
- **cannot** — needs something a self-hosted server does not issue.
- **open** — missing, deliberately deferred.

| Behaviour | Official evidence | Status | Notes |
| --- | --- | --- | --- |
| Change password, force re-login everywhere | `pages/change-password` | have | `POST /api/auth/change-password`, already wired to the account screen |
| Password minimum length | validation copy in the password page | have | server enforces; app shows the error verbatim |
| Per-conversation token split | usage sheet on the chat page | **adopted** | tap the context readout: input / output / cache read / cache write / reasoning |
| Cache hit rate | usage sheet wording | **adopted** | `cacheRead / (input + cacheRead)`; empty when nothing was sent |
| Cost per conversation | usage sheet | **adopted** | needs a matching row in the pricing table; otherwise says so, never `$0.00` |
| Attachment limits | attach sheet | **adopted** | 9 files per message, 20 MB each — the server enforces neither |
| Phone-local theme | theme page wording: local only, does not overwrite the server theme | have | `Store.appearance`, `Store.textScale` — never written to the server |
| Server-wide theme | same page, second section | **adopted (labelled)** | the old Appearance screen mixed local and server settings with no distinction; now two labelled sections |
| Message background image | appearance page | have, differently | uploaded to the server (`/api/theme/background`), so it follows the account rather than the phone |
| Reasoning effort | composer chip | have | `ComposerSheet.Reasoning` |
| Model picker with search | model sheet | have | `ModelPickerDialog` searches the live catalog |
| Workspace per conversation | workspace sheet | have | `POST /api/studio/sessions/{id}/workspace` |
| Workspace favourites | workspace sheet | have | `workspace/directories` with `favorite` |
| Runtime versions + activation | version management page | **adopted (rebuilt)** | live job queue with progress, CLI installs, delete, and a warning before downloading (a finished download auto-activates the managed runtime) |
| Logged-in devices | devices page | have, differently | `ConnectionsScreen` over `app-connections` |
| QR pairing for a Studio | scan-page copy | open | needs a camera + QR decoder; the payload already exists (`authorization-codes/lan` → `qr_payload`) |
| Run-finished notifications | notification section of the settings page | open | needs `POST_NOTIFICATIONS` and a foreground-friendly notify path |
| Session sharing (single chat / group) | `pages/session-share` | cannot | `POST /sessions/{id}/shares` returns `share_app_device_required`; self-hosted cannot issue an `app_access` device token (probed) |
| Cloud account, email, third-party login | login page | cannot | `/api/app/auth/me` and `/api/app/connections/claim` are 404 on a self-hosted server (probed) |
| MCU / desktop runtime handoff | bootstrap page | cannot | desktop-only feature |
| Local STT model download | voice settings | open | `stt/local-model` exists but pulls a large model; deferred |

## What changed in 0.16.0

1. **Conversation usage sheet** — `GET /api/studio/sessions/{id}/usage` (the
   published Studio route; the `/api/hermes/...` twin works too but is not in
   the OpenAPI document, and `ApiPathAlignmentTest` rejected it, correctly).
   Cache reads are billed at the cache rate when the pricing table has one and
   at the input rate otherwise, matching the server's own fallback.
2. **Attachment caps** — 9 files, 20 MB each, refused before upload with a
   localised message.
3. **Appearance split** — the screen now says which settings live on this
   phone and which are pushed to every device.

## Still open, ranked

1. **QR pairing** — highest value for a phone client; the server already
   produces the payload.
2. **Run-finished notification** — a run that ends while the app is in the
   background is currently silent.
3. **Local background image** — the official keeps the chat backdrop on the
   device; ours is account-wide.
4. **Local STT model** — large download, but it removes the round trip.

## 0.17.0 — chat look and chat events

### Events the official client listens to, and we now do too

Extracted from the official page bundle's own handler table, then verified with
a live probe against this server (two namespaces: `/chat-run`, which this app
uses, and `/`, which the web client uses).

| Event | What it carries | Why it mattered |
| --- | --- | --- |
| `message.interim` | whole message text | a model can answer with no deltas at all; the bubble stayed empty until completion |
| `reasoning.available` | nothing | lets the thinking fold appear before the first token |
| `session.activity.snapshot` | sessions already running | a run started on the desktop was invisible in the list until a manual reload |
| `session.workspace.updated` | `workspace` | the agent moving the workspace silently changed what the composer files into |
| `workspace.diff.completed` | `change_id`, `change` | "this run touched N files" was never said |
| `peer.user.message` | message | another device posting into the same conversation went unseen |
| `app.notification` | title/content/sessionId | the server's own completion notice was dropped |
| `abort.started` / `abort.timeout` / `abort.completed` | — | `abort.timeout` means the run did **not** stop; we used to claim it did |

Measured on a short live turn, the server delivered: `run.started`,
`message.delta`, `reasoning.delta` (49 frames), `reasoning.available`,
`usage.updated` (6), `session.activity` (2), `session.activity.snapshot`,
`session.workspace.updated`, `app.notification`, `run.completed`. The
`/` namespace delivered nothing for that run — `/chat-run` is the correct
namespace for a native client.

### Chrome and metrics, taken as numbers

`ChatMetrics.kt` records the official page stylesheet's measurements. No
stylesheet text is stored, only numbers and colour values.

| Surface | Official | Before |
| --- | --- | --- |
| Session row | 8/10 padding, 6dp radius, 16dp agent badge inline | 13/11 padding, 42dp portrait, chevron, 2-line title |
| Session title | 13sp / 18sp, weight 550, one line | titleSmall, 2 lines |
| Session clock | 11sp / 16sp muted | labelSmall |
| Session preview | 10sp / 14sp muted, one line, 3dp below title | 12sp fact line |
| Running indicator | 2dp rainbow track inside the row | 2dp bar plus a dot plus a separate status line |
| Header | 46dp, 3/14 padding, centred 15sp/22sp at weight 650, 38dp buttons | TopAppBar with 33dp avatar and a two-line title block |
| Message rhythm | 14dp between messages, 20dp gutter | 8dp rhythm, 10dp gutter |
| Bubble | one `--ink-bg-message` for both roles, 10dp radius, 10/14 padding | two-sided Telegram tint, 16dp with a 4dp pinched corner |
| Clock | outside the bubble: 24dp meta row, 11sp/16sp | inside the bubble, 10sp |
| Process strip | fixed 32dp, 26dp live tool chip, mono elapsed | variable height, status text with the tool glued on |

### Deliberately kept from the old build

Swipe-to-reply, per-chat drafts, long-press-only action menu, unread counter on
the jump pill. These are interaction logic, not paint, and the official client
does not have them — the user asked for them earlier.

### Not done, with the reason

- **Per-session unread dot** — the list route returns no message count and no
  last-message preview, so a dot would be a guess. The official client gets
  both from its own session store.
- **Last-message preview text** — same missing field. The preview slot shows
  source / profile / model instead of inventing text.
- **960dp readable cap** — a phone never reaches it; only matters on a tablet.

## Correction: `inbox` is a file queue, not a notification centre

The module inventory labelled `.inbox-*` as a notification inbox. The official
copy says otherwise:

- 「待添加文件」 — the title, with a count beside it
- 「选择文件添加到当前聊天，确认发送后才会上传。」
- 「添加到当前聊天 (N)」 and, with no chat open, 「请先选择一个聊天」
- 「清空全部」, and 「仅移除 App 暂存的副本，不会删除原文件。」
- per-row 「移除文件」

Its classes are `.inbox-trigger` (44dp floating button), `.inbox-drawer` (max 78vh,
560dp), `.inbox-check` (18dp, 6dp radius), `.inbox-add` (46dp, 13dp radius,
inverted). Nothing in it is a notification list.

So the parity work became the thing the official client actually does: accept
`ACTION_SEND` / `ACTION_SEND_MULTIPLE`, copy every stream into the app's own
cache, and hold them in a drawer until they are attached. The copy's promise —
that clearing does not touch the original — is only true because the app keeps
its own copy, so the code does exactly that, and a failed upload keeps the file
in the queue rather than dropping it.
