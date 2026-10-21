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
