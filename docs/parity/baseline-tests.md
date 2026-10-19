# Baseline build & test record

Recorded during the phase-1 inventory pass. **Read the outcome section before
trusting any "复刻完成" claim in this repo's history** — the Android
toolchain does not run on this host, so "the tests pass" has not been a
verifiable statement here.

## Host

| | |
|---|---|
| OS | Debian 13 (trixie) on **aarch64** |
| CPU / RAM | 2 cores / 11.9 GB (≈6.6 GB available during the attempt) |
| JDK (preinstalled) | OpenJDK **1.8.0_452** — too old for AGP 8.9.2 |
| Gradle | not installed |
| Android SDK | `/usr/lib/android-sdk` with `build-tools/debian` + `platform-tools` only — **no `platforms/`** |

Nothing in this project could therefore be compiled or tested as found.

## What was installed

`scripts/apk/setup-build-env.sh` provisions, entirely under `/opt/android-buildenv`
(plus JDK 17 from apt, which installs alongside JDK 8 rather than replacing it):

- OpenJDK 17 (`/usr/lib/jvm/java-17-openjdk-arm64`)
- Gradle 8.11.1
- Android cmdline-tools, `platforms;android-35`, `build-tools;35.0.0`

Build config under test: AGP 8.9.2, Kotlin 2.1.21, `compileSdk 35`, `minSdk 26`.

## Outcome: **could not run** — ARM64 aapt2 gap

`:app:testDebugUnitTest` fails in `:app:processDebugResources`, in two stages.

**Stage 1 — wrong architecture.** AGP resolves aapt2 from Maven; the downloaded
`aapt2-8.9.2-12782657-linux` is x86-64 and cannot execute here:

```
aapt2: cannot execute binary file
AAPT2 aapt2-8.9.2-12782657-linux Daemon #0: Daemon startup failed
```

**Stage 2 — override found, but too old.** Overriding with the system ARM64
aapt2 (`-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2`) gets aapt2 to execute,
but Debian's build-tools are `29.0.3` (Android 10 era) and cannot parse the
android-35 resource table:

```
ERROR: AAPT: LoadedArsc.cpp:94 RES_TABLE_TYPE_TYPE entry offsets overlap actual entry data.
ERROR: AAPT: ApkAssets.cpp:149 Failed to load resources table in APK
       '/opt/android-buildenv/sdk/platforms/android-35/android.jar'
error: failed to load include path .../android-35/android.jar
```

**There is no fix available.** The ARM64 artifact does not exist upstream:

```
aapt2-8.9.2-12782657-linux-arm64.jar  -> HTTP 404
aapt2-8.9.2-12782657-osx-arm64.jar    -> HTTP 404
```

Debian's newest `android-sdk-build-tools` candidate is `29.0.3+12`; Ubuntu's is
`27.0.1+12`. Google publishes Linux build-tools for x86-64 only.

Because `processDebugResources` is a hard prerequisite, this blocks
**all** Gradle tasks — unit tests, `compileDebugKotlin`, and APK assembly alike.
Emulator/ADB verification is likewise out of reach.

## What was verified instead

Static verification was used, and its limits are stated wherever it is cited.
The distinction matters: none of the below is a runtime observation.

| Check | Method | Result |
|---|---|---|
| Ink palette vs official APK tokens | `Theme.kt` parsed and compared against `docs/reference/apk-1.0.3/theme-tokens-*.json` | **1 real bug found and fixed** — see below |
| Login page metrics | `pages-login.css` `@media (max-width:600px)` compared line-by-line | 5 mismatches found and fixed |
| Composer container | `pages-index.css` `.composer-wrapper` compared | radius matches; `min-height` gap recorded |
| APK baseline identity | SHA-256 + signing cert + patch-marker scan | confirmed 1.0.3 pristine |

One row above was corrected on 2026-09-19 by the class-liveness pass: this check
originally cited `.input-wrapper`, which is **dead CSS** — a leftover of the
pre-rename composer in `AppSingleChatView` (`docs/parity/composer.md`). The
metrics that were compared (`border-radius:18px`, `min-height:78px`, the card's
background/border/shadow) are **identical** in the live `.composer-wrapper`, so
the finding stands and only the class name was wrong.

### The palette bug this caught

`Theme.kt` `inkSelectedBg(dark)` used `0x1FFFFFFF` (α 0.12). The official
`--ink-selected-bg` dark is `rgba(255,255,255,.18)` (α 0.18). The wrong value is
exactly `--ink-focus-ring`'s dark alpha — a copy-paste from the adjacent
function. Dark-mode selected/focus tints were rendering ~33% too faint.

After the fix, **23 of 23** mechanically comparable tokens match the APK exactly.
(`--ink-shadow-lg` is a multi-part shadow and the two banner art colours are not
`--ink-*` tokens, so those four are not mechanically comparable.)

This is why `OfficialInkTokenTest` was added: the audit that found the bug was a
one-off script, and a one-off script does not stop the bug coming back.

## Status of the new test

`android/app/src/test/java/xyz/rflg/hstudiodirect/OfficialInkTokenTest.kt` was
written but **has never been executed** — the aapt2 blocker above applies to it
too. Its parsing logic was validated by re-implementing it in Python and running
it against `Theme.kt`: it reports 23 match / 0 mismatch after the fix, and 22/1
before it. That is evidence the logic is right; it is **not** evidence the Kotlin
compiles. Treat the test as unverified until CI runs it.

## Unblocking this

Any one of:

1. Run the build on an x86-64 Linux host or CI (the repo's GitHub Actions
   `android.yml` already uses `ubuntu-latest`, which is x86-64 — **this is the
   likely reason the gap went unnoticed**).
2. Add a Gradle wrapper to the repo so local builds are reproducible.
3. Verify visual parity on a real device via ADB, which no static check replaces.

## Reproducing the attempt

```sh
sh scripts/apk/setup-build-env.sh
cd android
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/opt/android-buildenv/sdk \
/opt/android-buildenv/gradle-8.11.1/bin/gradle --no-daemon --console=plain \
  -Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2 \
  :app:testDebugUnitTest
```
