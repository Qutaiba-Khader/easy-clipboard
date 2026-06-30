# Easy Clipboard

A modern, open-source **clipboard history manager for Android** — a no-root /
no-Xposed successor to the long-dead Xposed app *Native Clipboard*
(`com.dhm47.nativeclipboard`). Easy Clipboard rebuilds the same idea —
*multi-clip history + paste into any field + a "Clipboard" button in the
text-selection menu* — using **sanctioned modern APIs**, so it runs on
**Android 8–15 with no root and no Xposed**.

> Status: ✅ **Working MVP.** A compiling, installable app (single Activity +
> Fragments, Java, Material 3). See the latest [Release](../../releases) for an
> installable APK, and [`docs/EASY_CLIPBOARD_DETAILS.md`](docs/EASY_CLIPBOARD_DETAILS.md)
> for the full implementation write-up.

## Features

- **Clip history grid** — the original's clip-card grid look (ported card,
  drawables, colours, `ClipAdapter`); tap to copy, double-tap to view full text,
  long-press to pin / edit / delete, swipe to delete, FAB to add.
- **Double-tap any text field → bottom clipboard panel** (via Accessibility): a
  keyboard-style panel slides up from the bottom; tap a clip to paste it into the
  field. No floating bubble. Top handle and a close (X) dismiss it; it sits above
  the system nav bar.
- **Clipboard keyboard (IME)** — a clip-tray keyboard that commits a chosen clip
  into the focused field and can read the clipboard on every Android version.
- **Selection-menu button** (`ACTION_PROCESS_TEXT`) — a "Clipboard" entry in any
  app's text-selection toolbar; pick a clip to paste it over the selection.
- **Background monitor** — a foreground service that captures copies while alive.
- **Shizuku global capture (optional, no root)** — records every copy from any app
  in the background by talking to the clipboard service as the shell UID. Degrades
  gracefully to the paths above when Shizuku is absent.
- **Settings** — history size, sort (new / pinned-first / pinned-last), text size,
  column count, plus a **Setup** tab that shows and gates each capability/permission.

## The one real constraint

**Android 10+ blocks background clipboard reads** for ordinary apps — only the
focused app or the active IME may read the clipboard. This is an OS rule, so the
app provides multiple sanctioned capture paths (IME, PROCESS_TEXT, foreground
monitor) and an optional Shizuku path for true global background capture.

## Build

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (debug-keystore signed, R8 off)
```

AGP 8.7.3 · Gradle 8.14 · JDK 17 · compileSdk 35 · minSdk 26 · targetSdk 35.

## Install & set up

1. `adb install -r app-release.apk` (or download the APK from the Release).
2. Open the app → **Setup** tab and enable: the **Clipboard keyboard**, the
   **Accessibility service**, and **Display over other apps**.
3. (Optional) Install [Shizuku](https://shizuku.rikka.app/), start it, and grant
   Easy Clipboard for global background capture.

## Background & analysis

- [`docs/EASY_CLIPBOARD_DETAILS.md`](docs/EASY_CLIPBOARD_DETAILS.md) — implemented
  app: architecture, components, the four capture/paste paths, the double-tap
  panel flow, storage, permissions, settings, build/release, limitations.
- [`docs/REVERSE_ENGINEERING_REPORT.md`](docs/REVERSE_ENGINEERING_REPORT.md) and
  [`docs/ORIGINAL_APP_PROFILE_AND_PLAN.md`](docs/ORIGINAL_APP_PROFILE_AND_PLAN.md)
  — the teardown of the original and the modernization plan.

## Credits

Concept and original implementation: *Native Clipboard* by **dhm47**
([github.com/DHM47/Native-Clip-Board](https://github.com/DHM47/Native-Clip-Board)).
Easy Clipboard is an independent ground-up rewrite inspired by it.

## License

MIT (to be added).
