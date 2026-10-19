# Parity record — Login (`pages/login`)

**Official source**: `docs/reference/apk-1.0.3/css/pages-login.css` from official
HStudio v1.0.3 (`bf069da2…20d62`).
**Compose target**: `android/app/src/main/java/xyz/rflg/hstudiodirect/MainActivity.kt`
(`LoginScreen`, `LoginBanner`, `LoginField`, `RememberCheck`, `LoginPrimaryButton`).
**Method**: static stylesheet comparison. **No device screenshot.**

## The version trap on this page

`pages-login.css` defines every metric **twice**: once unqualified and once
inside `@media screen and (max-width: 600px)`, plus a third `max-width: 360px`
block. The unqualified rules are the *tablet/desktop* variant and are
substantially larger — `.field-shell` is 48px tall with 13px padding and an 8px
radius, `.banner-title` is 24px, `.login-content` overlaps by 24px with a 26px
radius.

The app is phone-first, so **the `max-width: 600px` block is the correct ground
truth for a phone**. Reading only the unqualified rules yields a page that looks
plausible and is uniformly too large. Every value below is therefore quoted from
the phone block.

## Verified matches

These were already correct and were left alone. They are recorded so a later
change does not silently regress them.

| Selector | Official (phone) | Compose | Where |
|---|---|---|---|
| `.auth-title` | `font-size:18px; line-height:26px` | `18.sp` / `26.sp` SemiBold | `MainActivity.kt:406` |
| `.auth-description` | `margin-top:3px; 11px/17px` | 3dp, `11.sp`/`17.sp` | `MainActivity.kt:413` |
| `.auth-form` | `margin-top:16px` | 16dp | `MainActivity.kt:420` |
| `.field-group+.field-group` | `margin-top:12px` | 12dp | `MainActivity.kt:430` |
| `.field-label` | `margin-bottom:6px; font-size:11px` | 6dp, `11.sp` Medium | `MainActivity.kt:646` |
| `.field-shell` | `height:42px; padding:0 11px; border-radius:7px` | 42dp, 11dp, 7dp | `MainActivity.kt:667` |
| `.field-icon` | `width:18px; height:18px; margin-right:8px` | 18dp, 8dp | `MainActivity.kt:675` |
| `.password-toggle` | `width:28px; height:28px` | 28dp | `MainActivity.kt:454` |
| `.primary-button` | `height:42px; border-radius:7px; font-size:13px; margin-top:16px` | 42dp, 7dp, `13.sp`, 16dp | `MainActivity.kt:474` |
| `.banner-logo` | `width:38px; height:38px; border-radius:8px` | 38dp, 8dp | `MainActivity.kt:568` |
| `.banner-brand-name` | `font-size:13px; line-height:19px` | `13.sp`/`19.sp` | `MainActivity.kt:585` |
| `.banner-kicker` | `font-size:7px; line-height:10px` | `7.sp` | `MainActivity.kt:592` |
| `.banner-title` | `font-size:21px; line-height:29px` | `21.sp`/`29.sp`, −0.4sp | `MainActivity.kt:608` |
| `.banner-description` | `font-size:10px; line-height:15px` | `10.sp`/`15.sp` | `MainActivity.kt:620` |
| `.login-content` | `border-radius:22px 22px 0 0; margin-top:-18px; padding:22px 20px` | 22dp, −18dp, 22dp/20dp | `MainActivity.kt:395` |
| `--ink-focus-ring` | `box-shadow:0 0 0 3px var(--ink-focus-ring)` | 3dp halo, 10dp radius | `MainActivity.kt:658` |
| `.login-content-inner` | `max-width:375px` | `widthIn(max=375.dp)` | `MainActivity.kt:404` |

## Corrections applied this pass

Five values did not match. All were fixed in `MainActivity.kt`.

| # | Official (phone block) | Was | Now |
|---|---|---|---|
| 1 | `.login-banner, .banner-inner { min-height:200px }` | `heightIn(min = 182.dp)` | `200.dp` |
| 2 | `.banner-inner { padding: calc(16px + safe-top) 20px 36px }` | top 18dp, bottom 18dp | top 16dp, bottom 36dp |
| 3 | `.login-content { padding: 22px 20px calc(14px + safe-bottom) }` | bottom 42dp | bottom 14dp |
| 4 | `.banner-copy { max-width:300px }` | no max-width | `widthIn(max = 300.dp)` |
| 5 | `.banner-description { margin-top:5px }` | 4dp | 5dp |

Notes on each:

1. `182` is not a value in the stylesheet at all. It is `200 − 18`, i.e. someone
   folded the `-18px` content overlap into the banner's own height. That
   double-counts: the official banner is 200px tall and the content overlaps it
   by 18px *on top of that*. Net reveal is 200px, not 182px.
2. The banner's 36px bottom padding is what the `-18px` overlap eats into, which
   is why the two numbers are easy to swap. They were swapped here (18/18).
3. Same family of error — the old 42dp was the *button* height, not the content
   padding.
4/5. Small but free to fix.

## Deliberately not replicated

| Official | Why |
|---|---|
| `.social-login-button` (Google / GitHub), `.social-login-button.apple-login-button` (+ `--dark-surface` / `--pressed`) (Apple) | Authenticate against the vendor's cloud. Direct-connect has no equivalent; adding dead buttons would be worse than omitting them. |
| `.verification-row`, `.verification-button` | Email verification codes, cloud-gated. |
| `.legal-overlay`, `.legal-*` | Terms/privacy overlay. Should probably exist eventually for honesty, but the official text is vendor-specific. |

> **Class names corrected 2026-09-19.** This table used to list
> `.google-login-button` as the Google button's class. It is **dead CSS** — the
> rule (48px, `1px solid var(--ink-border)`, radius 8px) exists in
> `pages-login.css` in both releases, but no component ever emits the class:
> 0 occurrences in `app-service.js`, statically or dynamically. The shipped
> social row emits `social-login-button` for **both** Google and GitHub, and
> `social-login-button apple-login-button` for Apple (adding
> `apple-login-button--dark-surface` in the dark theme). Same failure mode as the
> composer's `.input-*` — a rule compiled for a template that no longer exists.

## Real gaps on this page

| Official | Status |
|---|---|
| `.api-route-switch`, `.api-route-compact-option`, `.api-route-heading` | **Not implemented.** The official login page lets the user pick between the cloud relay and a direct/LAN route. This project is *built around* direct connection and the official UI for it is missing. Highest-value remaining item on this page. |
| `.remember-check`, `.terms-option` | `RememberCheck` exists (`MainActivity.kt:705`); the terms checkbox does not. |
| `.auth-panel--compact` | Not modelled; the phone block is used unconditionally, which is correct for phones but means the tablet layout is unhandled. |

> **A row was removed from this table 2026-09-19: `.request-state` /
> `.request-state-title` are not a gap.** The rules exist in `pages-login.css`
> (`.request-state`: `margin-top:24px; padding:18px; 1px solid var(--ink-border);
> radius 8px; --ink-bg-secondary`; `.request-state-title`: 14px/600;
> `.request-state-copy`: 11px/18px muted) but **0 components emit any of the
> three**, in either release — dead CSS, not an unimplemented official element.
> The live surfaces on this page are `.field-error` (`margin:6px 0 0 4px;
> color:var(--ink-error); font-size:11px; line-height:17px`, inside a
> `.field-group`) for per-field failures, and `.button-loading` (16×16, 2px
> `currentColor` ring spinning `.8s`, `margin-right:9px`) inside
> `.primary-button` while a request is in flight. The port has the busy half
> (`MainActivity.kt:795`: a 16dp/2dp `CircularProgressIndicator`, though its gap
> is `8.dp` where the official is `9px`) but **not** `.field-error` — it reports
> login failures through the shared `ErrorNote` below the button
> (`MainActivity.kt:508`), which is a different surface from the official's
> per-field line. Worth a row of its own if the login page is revisited.

## Reproducing

```sh
# the phone-variant metrics, which are the ones that matter
python3 - <<'EOF'
import re
css = open('docs/reference/apk-1.0.3/css/pages-login.css', encoding='utf-8').read()
for m in re.finditer(r'@media([^{]*)\{', css):
    i, d = m.end(), 1
    while i < len(css) and d:
        d += (css[i] == '{') - (css[i] == '}')
        i += 1
    body = css[m.end():i-1]
    if 'login-banner' in body:
        print(body)
EOF
```
