# Easy Clipboard

A modern, open-source **clipboard history manager for Android** — a spiritual successor to the
long-dead Xposed app *Native Clipboard* (`com.dhm47.nativeclipboard`, last updated ~2016,
Android 7-only). Easy Clipboard rebuilds the same idea — *multi-clip history + paste into any
field + a "Clipboard" button in the text-selection menu* — using **sanctioned modern APIs**, so it
runs on **Android 8–15 with no root and no Xposed**.

> Status: 📐 **Design / spec phase.** This repo currently holds the reverse-engineering analysis
> of the original app and the architecture plan. App code lands next.

## Why a rewrite (not a patch)

The original is structurally obsolete:
- Its core relied on **Xposed hooks** into private framework classes (`android.widget.Editor.*`,
  Chromium/Gecko internals) that no longer exist past Android 7.
- It targets `targetSdk 25`, which modern Android won't run normally.
- Android 10+ blocks the "globally hook every copy" model outright.

See [`docs/REVERSE_ENGINEERING_REPORT.md`](docs/REVERSE_ENGINEERING_REPORT.md) and
[`docs/ORIGINAL_APP_PROFILE_AND_PLAN.md`](docs/ORIGINAL_APP_PROFILE_AND_PLAN.md) for the full
teardown and the feature-by-feature modernization plan.

## The one real limitation (read this)

**Android 10+ (API 29) blocks background clipboard reads** — only the *focused app* or the
*active keyboard (IME)* may read the clipboard. This is enforced by the **OS, not by any app
store**, so being off-store does not remove it. It is **not a blocker**; it just shapes the
architecture below.

## Architecture

Distributed off-store (APK / F-Droid), so no store-policy constraints on accessibility/clipboard use.

| Concern | Approach |
|---------|----------|
| **Capture / paste — baseline** | **Custom IME** (Compose keyboard with a clip-tray). An active IME may read the clipboard on every Android version. Always available, no special setup. |
| **Selection-menu "Clipboard" button** | **`ACTION_PROCESS_TEXT`** (API 23+). One Activity registered for `android.intent.action.PROCESS_TEXT` appears in the text-selection toolbar of *every* app, with zero hooks; returning replacement text pastes a chosen clip in place. |
| **Global "capture every copy" (background)** | **Shizuku** — runs under the ADB/shell UID, which is **exempt** from the Android 10+ background-read restriction. Gives true global capture **without root** (user installs Shizuku and starts it once via wireless/USB debugging). Optional; degrades to IME + PROCESS_TEXT when off. |
| **Auto paste-back (optional)** | Accessibility `ACTION_PASTE`. |
| **Storage** | Room (SQLite), optional encryption at rest. |
| **UI** | Kotlin + Jetpack Compose, Material 3. |
| **SDK** | minSdk 26, targetSdk 35. |

**No root. No Xposed.** Shizuku is the only privileged path and it is root-free.

## Planned features (ported from the original)

History with configurable size · pin clips · per-clip titles · sort (new / pinned-first /
pinned-last) · per-app blacklist · backup & restore · custom keyboard clip-tray · theming
(colors, collections) · sizing (text/window/columns) · boot persistence.

## Roadmap

- [ ] **MVP** — Compose IME with history tray, pin/sort/blacklist, `PROCESS_TEXT` button, Room storage.
- [ ] Shizuku global-capture mode (no-root) with graceful fallback.
- [ ] Themes / collections, sizing, backup & restore, onboarding.
- [ ] Optional accessibility auto-paste.

## Credits

Concept and original implementation: *Native Clipboard* by **dhm47**
([github.com/DHM47/Native-Clip-Board](https://github.com/DHM47/Native-Clip-Board)). Easy Clipboard
is an independent ground-up rewrite inspired by it.

## License

MIT (to be added).
