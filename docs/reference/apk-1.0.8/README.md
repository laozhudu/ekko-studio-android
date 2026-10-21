# Official Ekko Studio APK reference (v1.0.8)

Ground-truth design tokens extracted from the **vendor-signed official Android
app**, current as of 2026-10-21. Read-only reference material; never compiled into
this app.

## Provenance

| | |
|---|---|
| Source | `https://github.com/EKKOLearnAI/ekko-studio/releases/download/v1.0.8/EkkoStudio.apk` |
| Discovered via | `GET https://api.ekkostudio.xyz/api/studio/versions` → `mobile.channels.androidApk` |
| SHA-256 | `4d7fc3217de2b86262874ba45102378475d1cbe4654afb84b2606234eeb4138e` |
| Size | 41,337,633 bytes |
| Package | `com.ekkostudio.ai` |
| versionName / versionCode | `1.0.8` / `165` |
| uni-app id | `__UNI__1F41684` |
| Tokens | 50 per mode, extracted from `assets/apps/__UNI__1F41684/www/app.css` |

## Result: the palette did not change

The `--ink-*` palette in 1.0.8 is **value-for-value identical** to 1.0.4, which was
itself identical to 1.0.3:

| Comparison | Keys | Value differences |
|---|---|---|
| 1.0.3 vs 1.0.4 | same 50 | 0 |
| 1.0.4 vs 1.0.8 | same 50 | 0 |

So there is nothing to "upgrade" in the colour system across these five releases.
This directory is kept as the newest baseline so the next release can be diffed
against the current truth rather than an old one.

## Re-extracting for a future release

```bash
curl -L -o /tmp/EkkoStudio.apk \
  "$(curl -s https://api.ekkostudio.xyz/api/studio/versions \
     | python3 -c 'import json,sys;print(json.load(sys.stdin)["mobile"]["channels"]["androidApk"]["githubUrl"])')"

python3 scripts/extract_apk_tokens.py /tmp/EkkoStudio.apk docs/reference/apk-<version>

diff -u docs/reference/apk-1.0.8/theme-tokens-dark.json \
        docs/reference/apk-<version>/theme-tokens-dark.json
```

`OfficialInkTokenTest` pins `Theme.kt` to `theme-tokens-*.json`, so if a future
release does change a colour, that test fails and names the token.
