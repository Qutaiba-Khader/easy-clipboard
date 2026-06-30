# Native Clipboard — Full App Profile & Modernization Assessment

> Reverse-engineered in CT 200 (jadx 1.5.5 + dex2jar/Vineflower cross-check, apktool).
> Source APK: `Native Clipboard_4.8.1.apk` (md5 `4965df67b9a9c3e70abdeac58b4ebf1f`).

---

## 1. Identity

| Field | Value |
|-------|-------|
| App name | **Native Clipboard** ("Native Clip Board") |
| Package | `com.dhm47.nativeclipboard` |
| Version | 4.8.1 (versionCode 481) |
| minSdk / targetSdk | **15 / 25** → Android 4.0.3 … **7.1 only** |
| Author | "dhm47" / signed `CN=Abdulrahman Alghamdi` |
| Open source | **Yes** — `github.com/DHM47/Native-Clip-Board` (stated in about.html) |
| Origin | XDA forum project, ~2015–2016. **Dead/abandoned.** |
| Type | **Xposed module** + Accessibility + IME, single `classes.dex`, no native `.so` |
| Network | **None** — no `INTERNET` permission, no HTTP/socket/analytics code |

## 2. What it does

Turns Android's stock single-slot clipboard into a **multi-clip history manager**.
It injects a **"Clipboard" button** into the text-selection action bar of *every* app,
records everything you copy into a searchable history, and lets you tap an old entry to
paste it — with pinning, titles, sorting, blacklist, theming and an optional custom keyboard.

## 3. Full feature set (from settings + onboarding strings)

**Core**
- System-wide clip history (capture every copy globally).
- "Clipboard" button injected into the selection toolbar (`cbbutton`).
- Tap a saved clip → pastes into the focused field. Sentinel `//NATIVECLIPBOARDCLOSE//` coordinates paste-back.
- `pastefunction` — long-click PASTE opens the manager; `singlepaste` — auto-close after one paste.
- `smart_position` — overlay opens near the keyboard (uses `Keyheight` ratio).
- Pin clips (never trimmed), set a **Title** per clip, swipe-to-delete, drag action bar to move.

**Capture engines (3, user-selectable)**
- **Xposed** (`monitorservice` off) — hooks `ClipboardManager.setPrimaryClip/setText` + framework `Editor.*ActionModeCallback`; per-app shims for **Chrome/Chromium WebView, Firefox/Gecko, HTC**.
- **Monitor service** (`monitorservice` on) — broadcast `DHM47.Xposed.ClipBoardMonitor` → `ClipMonitorService`.
- **Accessibility service** — non-Xposed capture/paste via `AccessService` (+ `key_monitor`, `combos`).

**Management**
- History size (`history`, default 25, `999999`=unlimited), Sort (`newfirst`/`pinnedfirst`/`pinnedlast`).
- **Blacklist** apps (per-package exclusion).
- **Backup / Restore** to a file path (`backup`, `restore`, `backuppath`).
- Boot-persistence (`BootReceiver`), optional persistent notification.

**Custom keyboard (IME)** — two services: `ClipboardIMEInteg` (integrated) & `ClipboardIMEInde` (independent)
- Keyboard clip-tray, tutorial, auto-return, single-paste, keyboard theme.

**Theming / sizing** — bg/clip/pinned/text colors, saveable theme "collections", text size, window size, column count.

## 4. Components

| Type | Class | Role |
|------|-------|------|
| Activity | `Setting` (launcher) | Settings UI |
| Activity | `ClipBoardA` | Transparent overlay clip picker |
| Activity | `Intro`, `IntroKeyboard` | Onboarding (AppIntro lib) |
| Service | `ClipBoardS` | Bound list service (holds clips) |
| Service | `AccessService` | AccessibilityService capture/paste |
| Service | `ClipMonitorService`, `ClipMonitorLegacy` | Persist captured clips |
| Service | `ClipboardIMEInteg/Inde` (`ClipboardIMEBase`) | Input methods |
| Receiver | `BootReceiver` | Re-arm on boot |
| Receiver | `ClipMonitorReceiver` (exported) | Receives capture broadcast |
| Xposed | `xposed.XposedMod` | All framework hooks |

**Data model** — `Clip{ text, title, time, pinned, position }` (Serializable),
persisted via Java serialization to private file **`Clips2.9`**. Prefs:
`..._preferences`, `..._xposed_preferences` (XSharedPreferences), `..._blacklist`.

**Permissions** — `SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`, `BIND_ACCESSIBILITY_SERVICE`,
`BIND_INPUT_METHOD`, `WRITE_EXTERNAL_STORAGE`. (No INTERNET.)

**3rd-party libs** — AppCompat-v7, Material Dialogs (afollestad), AppIntro (paolorotolo),
Android Swipe-to-Dismiss (Roman Nurik). All benign OSS.

---

## 5. Why it's dead on modern Android

1. **Xposed-locked.** The core hooks target *private* framework classes
   (`android.widget.Editor.TextActionModeCallback`, `SelectionActionModeCallback`,
   Chromium/Gecko internals) that changed/vanished after Android 7. Even under LSPosed
   the hooked signatures no longer exist on Android 10–15.
2. **`targetSdk 25`.** Android 14+ refuses to install/run apps targeting such old SDKs normally.
3. **Clipboard privacy wall (Android 10 / API 29).** Apps **can no longer read the clipboard in
   the background** — only the focused app or the active IME can. The whole "globally hook every
   copy" model is impossible without **root/Xposed** now.
4. **Policy.** Google Play bans accessibility-service use by non-accessibility apps, and bars
   apps that auto-read the clipboard — so a Play release of the old design is a non-starter.

---

## 6. Can we build a NEW, modern-compatible app? ✅ Yes — with a redesigned architecture

The *value* (multi-clip history + paste-into-any-field + selection-menu button) is fully
achievable **without Xposed or root** using sanctioned APIs. What changes is the **capture model**.

### The key modern replacement
- **`ACTION_PROCESS_TEXT` (API 23+)** is the official successor to the Xposed selection-menu
  button. Register one Activity with
  `<intent-filter><action android:name="android.intent.action.PROCESS_TEXT"/>
  <category android:name="android.intent.category.DEFAULT"/><data android:mimeType="text/plain"/></intent-filter>`
  → a "Clipboard" item appears in the text-selection toolbar of **every app, system-wide**,
  zero hooks. Returning `EXTRA_PROCESS_TEXT` writes replacement text back into the field
  → that *is* the "pick an old clip and paste it in place" feature.
- **IME (keyboard)** path — an active input method *is* allowed to read the clipboard. The app
  already ships IME services; a Compose keyboard with a clip-tray gives capture **and** paste
  on every field, all versions, Play-Store-legal. (This is how Gboard's clipboard works.)
- **Shizuku** — for the power-user **global background "capture everything" mode** *without root*.
  Shizuku runs a privileged process under the **ADB/shell UID** (started once via wireless debugging
  or a PC), and lets the app call system services with shell privileges. The shell identity is
  **not subject to the Android 10+ foreground clipboard restriction**, so the app can read every
  copy in the background (e.g. via the clipboard system service / `cmd clipboard`) — the closest
  legal substitute for the old Xposed global hook. Requires the user to install the Shizuku app and
  start it (no root, no unlocked bootloader).
- **Accessibility service** (optional) — for auto paste-back / auto-capture assist; great for
  sideload/F-Droid builds, not for Play.

### Hard limitation to set expectations
Silent **background auto-capture of *every* copy** is blocked on Android 10+ for ordinary apps.
A modern app captures when: (a) its IME is active, (b) the user invokes the PROCESS_TEXT button,
(c) the app is foregrounded, or (d) **Shizuku is enabled** → full global background capture, no root.
Plan the UX around IME + PROCESS_TEXT as the always-available baseline, with Shizuku as the opt-in
upgrade for users who want true "every copy" history.

### Recommended new architecture
| Concern | Old (4.8.1) | New build |
|---------|-------------|-----------|
| Language/UI | Java + XML views | **Kotlin + Jetpack Compose, Material 3** |
| Min/target SDK | 15 / 25 | **minSdk 26, targetSdk 35** |
| Capture | Xposed global hook | **IME clip-tray** (primary) + **PROCESS_TEXT** + **Shizuku** (global, no-root) + optional Accessibility |
| Selection button | Xposed `Editor` hooks | **`ACTION_PROCESS_TEXT` activity** |
| Storage | Java-serialized `Clips2.9` | **Room (SQLite)**, optional SQLCipher encryption |
| Paste-back | clipboard sentinel listener | PROCESS_TEXT return / IME commitText / Accessibility `ACTION_PASTE` |
| Backup | manual file path | Room export + Android Auto Backup (or off) |
| Privacy | `allowBackup=true`, plaintext | encrypted at rest, `allowBackup=false`, auto-expiry |
| Distribution | Xposed module | **F-Droid + APK** (Play-compatible if IME/PROCESS_TEXT only) |

### Effort estimate
- **MVP** (Compose IME with history tray + pin/sort/blacklist + PROCESS_TEXT button + Room): ~1–2 weeks.
- **Full parity** (themes/collections, accessibility auto-paste, **Shizuku** global-capture mode,
  backup/restore, onboarding): ~3–5 weeks.

### Shizuku integration notes (the no-root global path)
- Add the `dev.rikka.shizuku:api` + `:provider` libraries; declare the Shizuku provider in the manifest.
- At runtime: check `Shizuku.pingBinder()` → request the Shizuku permission → bind the privileged
  user-service. Through it, talk to the clipboard system service with shell privileges to receive
  every clipboard change in the background.
- Pure **no-root**: the user installs the Shizuku app and starts it once via wireless/USB debugging
  (survives until reboot; or auto-start via an add-on). No bootloader unlock, no Magisk.
- Degrade gracefully: if Shizuku isn't running, fall back to IME + PROCESS_TEXT capture automatically.

### Verdict
The old APK can't be patched into compatibility — its mechanism is structurally obsolete. But a
**ground-up Kotlin/Compose rewrite is very feasible**, keeps ~90% of the user-facing features,
runs on Android 8–15, and needs **no root and no Xposed**. Baseline capture uses IME + PROCESS_TEXT
(works everywhere, Play-Store-legal); **Shizuku** is the opt-in upgrade that restores true global
"capture every copy" history **without root**. We can reuse this app's UX, settings layout, and
data model as the spec.
