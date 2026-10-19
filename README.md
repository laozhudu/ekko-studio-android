# ekko-studio-android

Android client for a self-hosted [Ekko Studio](https://github.com/EKKOLearnAI/ekko-studio).

> **Derived from [mrz2333/hermes-studio-mobile](https://github.com/mrz2333/hermes-studio-mobile) (MIT).**
> The Android sources under `android/` are adapted from that project. Original
> copyright and the MIT license are preserved in `LICENSE`. This project is not
> affiliated with EKKOLearnAI.

## Status

Bootstrap stage: the upstream baseline is imported verbatim so the build can be
verified in CI before any product change. Feature work starts only after a green
build — no more unverified code drops.

## Build

Requires JDK 17 and Android SDK platform 35. CI pins Gradle 8.11.1.

```bash
cd android
gradle assembleDebug
```

## Structure

```
android/    Android app (Kotlin + Jetpack Compose)
docs/       Upstream documentation
scripts/    Upstream helper scripts
VERSION     Shared version marker
```

## What the baseline already does

Chats with streaming over the `/chat-run` socket (with REST fallback), groups,
agent tools, jobs, Kanban, channels, skills, plugins, MCP, profiles and settings
— all native screens. See `android/README.md` for the full list.

## License

MIT — see [LICENSE](LICENSE).
