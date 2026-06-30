# Reverse Engineering Report — Native Clipboard 4.8.1

**Analyzed in:** CT 200 (code-cc) — jadx 1.5.5, apktool, apksigner
**Source:** https://rclone-public.websnake.org/storage/Native%20clipboard/Native%20Clipboard_4.8.1.apk
**Artifacts:** `/mnt/Shared1Tb/re-work/native-clipboard/{apktool_out,jadx_out}`

## Identity
| Field | Value |
|-------|-------|
| App name | Native Clipboard |
| Package | `com.dhm47.nativeclipboard` |
| Version | 4.8.1 (versionCode 481) |
| minSdk / targetSdk | 15 / 25 (Android 4.0.3 → 7.1) |
| Size / DEX | 2.8 MB, single `classes.dex` (3.3 MB), **no native `.so`** |
| Signed by | `CN=Abdulrahman Alghamdi` (SHA-256 `764149…f6f3f`) — self-signed dev cert |
| DEBUG | false (release build) |

## What it is
An **Xposed module** (`assets/xposed_init` → `com.dhm47.nativeclipboard.xposed.XposedMod`,
`xposedmodule=true`, `xposedminversion=30+`) that adds a **clipboard history manager** to
Android. It injects a "Clipboard" button into the native text-selection action bar of *every*
app, captures everything copied, stores a history, and lets you pick an old clip to paste —
making the stock single-slot clipboard behave like a multi-clip manager. This is the
well-known XDA/F-Droid app by developer "dhm47".

It is **not malware.** No `INTERNET` permission, no network/HTTP/socket code, no analytics,
no `Runtime.exec`/`ProcessBuilder`, no `DexClassLoader`/dynamic code loading. All clipboard
data stays on-device.

## Architecture — three capture paths
1. **Xposed (primary, `XposedMod.java`):**
   - `initZygote` hooks `ClipboardManager.setPrimaryClip` / `setText` system-wide → on any copy
     (except its own package) fires broadcast `DHM47.Xposed.ClipBoardMonitor` via `SendClip()`.
   - `handleLoadPackage` hooks the framework text-selection callbacks
     (`Editor.TextActionModeCallback`, `SelectionActionModeCallback`, plus per-app shims for
     **Chrome/Chromium WebView, Firefox/Gecko, HTC** text selection) to add menu item id `1259`
     "Clipboard". Tapping it launches `ClipBoardA` (transparent overlay activity).
   - Paste-back is signalled through the real clipboard with sentinel string
     **`//NATIVECLIPBOARDCLOSE//`** and a one-shot `OnPrimaryClipChangedListener`.
   - Also hooks the app's own `Setting.isModuleEnabled()` → returns true (so the Settings screen
     knows the module is active).
2. **AccessibilityService (`AccessService.java`):** non-root alternative to Xposed. Watches
   events, binds `ClipBoardS`, reads the saved clip list, can inject paste via accessibility
   actions. Declared with `BIND_ACCESSIBILITY_SERVICE` + `@xml/acces_config`.
3. **Legacy monitor (`ClipMonitorService` / `ClipMonitorLegacy` / `ClipMonitorReceiver`):**
   receives the `DHM47.Xposed.ClipBoardMonitor` broadcast and writes to storage.

## Storage & data model
- **`Clip`** (Serializable): `text, title, time, pinned, position`.
- Persisted with Java serialization to private file **`Clips2.9`** in app-internal storage
  (`openFileInput/openFileOutput`) — *not* world-readable.
- History cap from pref `history` (default 25; `999999` = unlimited). Pinned clips are never
  trimmed. Sorting: `newfirst` / `pinnedfirst` / `pinnedlast`.
- **Blacklist:** SharedPreferences `..._blacklist` — listed packages are excluded from capture.
- Prefs: `..._preferences` (main) and `..._xposed_preferences` (read cross-process via
  `XSharedPreferences`). Flags seen: `monitorservice, cbbutton, pastefunction, singlepaste`.

## Components (AndroidManifest)
- `Setting` (launcher, `:setting`), `Intro`/`IntroKeyboard` onboarding.
- `ClipBoardA` (transparent picker activity), `ClipBoardS` (bound list service).
- Two IMEs: `ClipboardIMEInteg` (integrated) & `ClipboardIMEInde` (independent) — optional
  keyboard front-ends (`BIND_INPUT_METHOD`).
- `BootReceiver` (re-arm on boot), `ClipMonitorReceiver` (exported, action
  `DHM47.Xposed.ClipBoardMonitor`).

## Permissions (all local-only)
`SYSTEM_ALERT_WINDOW` (overlay picker), `RECEIVE_BOOT_COMPLETED`, `BIND_ACCESSIBILITY_SERVICE`,
`BIND_INPUT_METHOD`, `WRITE_EXTERNAL_STORAGE`. **No INTERNET.**

## Third-party libraries
- `com.afollestad.materialdialogs` — Material Dialogs (UI).
- `com.github.paolorotolo.appintro` — AppIntro (onboarding slides).
Both benign, well-known OSS UI libs.

## Security / privacy assessment
| Aspect | Finding |
|--------|---------|
| Data exfiltration | **None** — no network capability or code at all. |
| Sensitive data scope | By design it logs **all clipboard text globally** (passwords, OTPs, etc.) into `Clips2.9`. Risk is local-only: any process with root, or a backup, could read the history. `allowBackup=true` + `fullBackupContent` means the clip history is included in ADB/cloud backups. |
| Exported surface | `ClipMonitorReceiver` is `exported=true` with a custom action — another app could spoof `DHM47.Xposed.ClipBoardMonitor` to **inject fake clip entries** (low impact, no read-back). `ClipBoardA` is exported. |
| Dynamic code / obfuscation | None. Code is clean, not obfuscated (real class/method names). |
| Verdict | Legitimate utility. Only inherent concern is that a global clipboard history is sensitive at rest. |

## Dual-engine decompile + API extraction (android-reverse-engineering skill)
Cross-validated the jadx output with a second engine (dex2jar → Vineflower), all in CT 200.

- **dex2jar** `NativeClipboard_4.8.1.apk → nc.jar` (3.25 MB) → **Vineflower** `vine_out/`.
- **Parity:** both engines produced **43 `.java` files** for `com.dhm47.*`. Only **4** jadx
  classes carry decompile warnings; Vineflower renders those cleanly as a cross-check
  (e.g. the Firefox `onActionItemClicked$139dd3d0` / `FFindex` switch in `XposedMod`).
- **API sweep** (`find-api-calls.sh`) over the app package — **no Retrofit / OkHttp / Volley /
  HttpURLConnection / API keys / auth**. The *only* two URL references in the entire app:
  | Where | URL | Purpose |
  |-------|-----|---------|
  | `introduction/IntroBoth.java:41` | `http://forum.xda-developers.com/showpost.php?p=62089779` | "open thread" VIEW intent (browser) |
  | `About.java:10` | `file:///android_asset/html/about.html` | local About page in a WebView |
- **`assets/html/about.html` confirms provenance:** *"Native Clip Board is an open source
  project available at github.com/DHM47/Native-Clip-Board"* — the legitimate OSS app. Credits
  list AppCompat, Material Dialogs (afollestad), Swipe-to-Dismiss (Roman Nurik), etc.

**Conclusion (both engines agree):** no remote API surface, no network code, no exfiltration.
Findings from the jadx pass stand fully corroborated.

## Reproduce
```bash
# in CT 200
cd /mnt/Shared1Tb/re-work/native-clipboard
apktool d -f NativeClipboard_4.8.1.apk -o apktool_out      # manifest + smali + res
jadx -d jadx_out NativeClipboard_4.8.1.apk                 # java (exit 3 = partial, fine)
```
