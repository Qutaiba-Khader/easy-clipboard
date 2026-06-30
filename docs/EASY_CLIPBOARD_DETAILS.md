# Easy Clipboard — Implementation Details

A modern, no-root / no-Xposed clipboard history manager for Android 8–15, rebuilt
from the dead Xposed app *Native Clipboard* (`com.dhm47.nativeclipboard`). This
document describes the **implemented MVP** (not the original design plan).

- Package / applicationId: `com.easyclipboard.app`
- Language: Java · UI: AppCompat + Material 3 + RecyclerView (single Activity + Fragments)
- AGP 8.7.3 · Gradle 8.14 · JDK 17 · compileSdk 35 · minSdk 26 · targetSdk 35

---

## 1. Overview

Easy Clipboard keeps a history of copied text and lets you paste any past clip
back into any app. It reuses the original's clip-card grid look (the ported
`textview.xml` card, drawables, colours and `ClipAdapter`). Because Android 10+
forbids background clipboard reads from ordinary apps, capture/paste is provided
through four sanctioned entry points plus an optional Shizuku privileged path.

The headline feature: **double-tap any text field in any app → a keyboard-style
clipboard panel slides up from the bottom → tap a clip to paste it into that
field**, driven entirely by the Accessibility service (no floating bubble).

---

## 2. Architecture

```
EasyClipApp (Application)
 ├─ HiddenApiBypass.addHiddenApiExemptions("")   (enables Shizuku reflection)
 └─ ClipRepository.get()  → warms the in-memory cache on a background thread

MainActivity (single Activity, BottomNavigationView)
 ├─ SetupFragment       — capability/permission status + fixes (default tab)
 ├─ ClipboardFragment   — the clip grid (RecyclerView + ClipAdapter)
 ├─ SettingsFragment    — PreferenceFragmentCompat
 └─ AboutFragment       — WebView (assets/about.html)

ProcessTextActivity     — PROCESS_TEXT selection-menu entry (dialog theme)

service/ClipboardMonitorService          — foreground service, in-process capture + Shizuku host
service/ClipboardAccessibilityService    — double-tap detection + bottom panel + paste-back
ime/ClipboardImeService                  — clip-tray keyboard
shizuku/ShizukuClipboardManager          — optional global background capture

data/ClipRepository      — in-memory cache + async disk (clips.dat)
model/Clip, model/ClipList
util/comparators/{NewFirst,PinnedFirst,PinnedLast}
ui/ClipAdapter, ui/ShowClipDialog
```

### Data layer — `data/ClipRepository`
Single in-memory source of truth (a `List<Clip>`), the only owner of disk I/O.

- Loaded from `clips.dat` (`ObjectInputStream`) **once**, on a background
  single-thread `ExecutorService`, kicked off at process start by `EasyClipApp`.
- Reads are served from memory — nothing on the panel-show path touches disk.
- Writes serialize a defensive **snapshot** to disk on the same executor, so
  mutations never block the UI / accessibility main thread.
- `addClip` is the faithful port of the original `Util.addClip`: dedupe by text
  (bump time), else insert at index 0, enforce the `history` size pref
  (`999999` = unlimited; pinned clips are never trimmed), then sort by the `sort`
  pref (`newfirst` / `pinnedfirst` / `pinnedlast`).
- Observers (`Runnable`s) are notified on the main thread so a visible grid/panel
  refreshes when a clip is captured in the background.
- Thread-safety: all mutators are `synchronized(this)`; the loader uses the same
  monitor and only applies the on-disk list if memory is still empty (never
  clobbers a clip captured during the startup window). UI mutates on the main
  thread; background captures `post` to the main thread before mutating.

### `model/Clip`, `model/ClipList`, comparators
Ported verbatim (logic preserved, package modernised). `Clip` is `Serializable`
(persisted via `ObjectOutputStream`); `ClipList` is a `Parcelable` `ArrayList<Clip>`.

### `ui/ClipAdapter`
Ported from the original adapter (`android.support.v7` → `androidx`), same
`textview.xml` card and colouring rules. Performance-tuned: colour/text-size/theme
settings are read from `SharedPreferences` **once** (constructor + `refreshSettings()`)
and cached in fields — never per bind; no static `Context` is held; the per-item
`GestureDetector` is built once per ViewHolder (in `onCreateViewHolder`).
Gestures: single tap = `onItemClicked`, double tap = `onItemDoubleTap`,
long-press = `onItemLongClicked` (routed via an explicit `OnItemListener` so it
works from Fragments, the IME, the PROCESS_TEXT activity and the overlay panel).

---

## 3. The four capture / paste paths (and how each works)

1. **Custom IME — `ime/ClipboardImeService`** (`InputMethodService`)
   A horizontal `RecyclerView` clip tray. Tapping a clip
   `getCurrentInputConnection().commitText(...)` into the focused field. When the
   input view is shown it reads the current system clipboard (an IME is allowed
   to) and adds it to history. Registered via `res/xml/method.xml` + manifest
   service with `BIND_INPUT_METHOD` / `action android.view.InputMethod`.

2. **Selection-menu button — `ProcessTextActivity`** (`ACTION_PROCESS_TEXT`)
   Appears in the text-selection toolbar of every app. On launch it saves the
   selected text to history and shows the clip grid; single-tap returns the
   chosen clip as `EXTRA_PROCESS_TEXT` (pasting it over the selection) unless the
   caller set `EXTRA_PROCESS_TEXT_READONLY`; double-tap shows the full text; a
   close (X) finishes without pasting. Dialog-themed activity.

3. **Foreground monitor — `service/ClipboardMonitorService`** (`START_STICKY`)
   Posts a `NotificationChannel` notification (`FOREGROUND_SERVICE_SPECIAL_USE`)
   and registers a `ClipboardManager.OnPrimaryClipChangedListener`; on change it
   adds non-sentinel text to history. Works while the process is alive (app
   foreground / IME up), within OS limits. Also the **host that keeps the process
   (and the Shizuku in-process listener) alive** — on `onCreate` it starts Shizuku
   global capture if available + granted, and stops it on `onDestroy`.

4. **Shizuku global capture — `shizuku/ShizukuClipboardManager`** (optional, no root)
   When Shizuku is running and granted, the framework clipboard service is
   obtained via `SystemServiceHelper.getSystemService("clipboard")` →
   `ShizukuBinderWrapper` → `IClipboard$Stub.asInterface`, so calls run as the
   **shell UID** (`com.android.shell`) which the OS lets read the clipboard freely.
   A framework `IOnPrimaryClipChangedListener` (declared as an AIDL so we can
   extend its `.Stub`) fires on every change → read clip → save new/deduped text.
   A **version-adaptive reflection helper** builds each call's argument array from
   the method's `getParameterTypes()` (callingPackage `com.android.shell`, null
   `attributionTag`, `userId` 0, `deviceId` 0) to cover the differing
   `getPrimaryClip` / `add`/`removePrimaryClipChangedListener` signatures across
   Android 8–15. If the listener registration throws, it falls back to a
   `HandlerThread` poll (~2000 ms). Everything is guarded — the rest of the app
   works unchanged when Shizuku is absent or denied. `HiddenApiBypass` (called in
   `EasyClipApp`) is required so the process may reflect on the hidden `IClipboard`.

   > Note: `startGlobalCapture()` is real (registers the listener / starts the
   > poll) but is hardware-dependent; see Known limitations.

### Auto paste-back (Accessibility `ACTION_PASTE`)
Used by the bottom panel and exposed as `pasteToFocused()`:
`getRootInActiveWindow().findFocus(FOCUS_INPUT)` → if editable,
`performAction(ACTION_PASTE)`.

---

## 4. Double-tap → bottom panel flow (`ClipboardAccessibilityService`)

1. **Detection** (mirrors the original `AccessService.Click()`): the config listens
   to `typeViewClicked|typeViewLongClicked|typeViewFocused|typeViewTextSelectionChanged`
   with `canRetrieveWindowContent` + `canRequestFilterKeyEvents`. On
   `TYPE_VIEW_CLICKED` of an `isEditable()` node, if
   `System.nanoTime() - lastEditableClickNanos < 300ms` it is a **double-tap** →
   `showClipboardPanel()`. The timestamp is always updated and reset to 0 when the
   source package changes (so a tap in app A then app B isn't a false double-tap).
   `onAccessibilityEvent` does nothing else — it stays cheap because it runs for
   every UI event system-wide.

2. **Panel**: a `TYPE_APPLICATION_OVERLAY` window (uses the Display-over-other-apps
   permission; `TYPE_PHONE` below API 26). `MATCH_PARENT` width, height = `windowsize`
   pref (default 280 dp), `gravity=BOTTOM`, flags
   `FLAG_NOT_FOCUSABLE | FLAG_WATCH_OUTSIDE_TOUCH` so it never steals focus from
   the text field. **No `FLAG_LAYOUT_IN_SCREEN`** + an `OnApplyWindowInsetsListener`
   that pads the panel up by the navigation-bar inset, so the system
   back/home/recent stay visible and tappable. The view + adapter are **inflated
   once on connect** and reused; show only rebinds the in-memory list + cached
   settings + span, then `addView`, then a ~130 ms `DecelerateInterpolator`
   slide-up. Content reuses the original `textview.xml` card + `ClipAdapter` grid.

3. **Dismiss**: the close (X), the **top handle bar**, BACK (`onKeyEvent`), an
   outside touch (`ACTION_OUTSIDE`), or a paste. Removal is guarded against double
   add/remove (parent check + `panelShowing` flag); `onInterrupt`/`onUnbind`/
   `onDestroy` force-remove the panel and unregister the repo observer.

4. **Paste-back**: on a clip tap → save to history + `setPrimaryClip`, then 100 ms
   later `getRootInActiveWindow().findFocus(FOCUS_INPUT)` and, if editable,
   `performAction(ACTION_PASTE)`; otherwise a toast fallback. Panel then dismisses.

---

## 5. Storage model

- File `clips.dat` in the app's internal `filesDir`, written with
  `ObjectOutputStream` (a snapshot `ArrayList<Clip>`), read with `ObjectInputStream`.
- In-memory list is the source of truth; disk is a background-only mirror.
- `Clip` fields: `text`, `title`, `time`, `pinned`, `position` (`serialVersionUID`
  fixed for forward compatibility).

---

## 6. Permissions (and why each is needed)

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | `ClipboardMonitorService` runs as a foreground service (target 35 requires the typed permission + a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`). |
| `POST_NOTIFICATIONS` | Show the foreground-service notification on Android 13+. |
| `SYSTEM_ALERT_WINDOW` | Draw the bottom clipboard panel over other apps (the overlay window). |
| Accessibility (`BIND_ACCESSIBILITY_SERVICE`) | Detect the double-tap and paste into the focused field (user-granted in Settings). |
| IME (`BIND_INPUT_METHOD`) | The clip-tray keyboard (user-enabled in Settings). |
| Shizuku provider (`INTERACT_ACROSS_USERS_FULL` on the provider) | Standard Shizuku `ShizukuProvider` declaration; only active when the user runs Shizuku. |

No `INTERNET`, no storage, no contacts — the app is fully local.

---

## 7. Settings (preference keys)

Ported from the original `pref_*`; `SettingsFragment` is a `PreferenceFragmentCompat`
over `res/xml/preferences.xml`. Keys consumed by the code:

| Key | Type | Default | Used by |
|-----|------|---------|---------|
| `history` | int (SeekBar) | 25 | `ClipRepository` history cap (`999999` = unlimited) |
| `sort` | list | `newfirst` | `ClipRepository.sort` (`newfirst`/`pinnedfirst`/`pinnedlast`) |
| `txtsize` | int | 20 | `ClipAdapter` text size |
| `columncount` | int | 0 (auto) | grid span (0 → screenWidth/160dp, min 2) |
| `clpcolor` / `pincolor` / `txtcolor` | int | card defaults | `ClipAdapter` colours |
| `windowsize` | int | 280 | overlay panel height (dp) |
| `keyboard_theme` | string | `same` | `ClipAdapter` (IME mode) |
| `blacklist`, `pastefunction`, `cbbutton`, `singlepaste`, `monitorservice`, `notification`, `keymonitor` | — | — | ported UI keys (placeholders / future) |

The **Setup tab** surfaces and gates the runtime capabilities: Notifications,
Clipboard keyboard (IME), Accessibility service, Display-over-other-apps, the
monitor service (start/stop), and Shizuku (request/start/stop) — each row shows an
enabled/not-enabled status and a button that opens the right system screen.

---

## 8. Build & release

```bash
# debug
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk

# release (signed with the Android debug keystore, R8 OFF)
./gradlew assembleRelease      # -> app/build/outputs/apk/release/app-release.apk
```

- `local.properties` must contain `sdk.dir=/opt/android-sdk` (CT 200).
- The Gradle wrapper points at the cached `gradle-8.14-bin.zip`.
- Release signing uses `~/.android/debug.keystore` (alias `androiddebugkey`,
  password `android`) purely so the APK is installable; **R8/minify is disabled**
  so the Shizuku/AIDL reflection targets aren't stripped or renamed. For a real
  store release, swap in a private keystore and add `-keep` rules before enabling
  R8.

Install: `adb install -r app-release.apk`. Then in the app's **Setup** tab enable
the keyboard, the Accessibility service, and Display-over-other-apps; optionally
install Shizuku and grant it for global background capture.

---

## 9. Known limitations

- **Double-tap detection varies per app/keyboard.** Many apps don't emit
  `TYPE_VIEW_CLICKED` for editable fields on every tap (some emit focus/selection
  events, or coalesce), so the 300 ms window can miss or misfire. Threshold and
  event set may need per-device tuning.
- **`ACTION_PASTE` behaviour varies.** Some fields/IMEs ignore or mis-place the
  paste; a few apps drop focus when the overlay appears, in which case
  `findFocus(FOCUS_INPUT)` returns null and we fall back to a toast (the clip is
  still on the system clipboard).
- **Shizuku global capture is hardware/version-dependent.** The reflective
  `IClipboard` call path is built to span Android 8–15, but OEM skins
  (Samsung/MIUI) and the Android 14/15 `deviceId` parameter can still reject a
  call; on failure the code falls back to polling. Needs real-device validation.
- **Overlay placement** above the nav bar relies on the device dispatching nav-bar
  insets to a `TYPE_APPLICATION_OVERLAY`; verify on gesture-nav vs 3-button.
- Background capture only runs while the process is alive (foreground service / IME
  / Shizuku host); this is the OS's Android 10+ restriction, by design.

---

## 10. Code review findings & fixes (finishing pass)

A genuinely thorough pass; the app was already structurally sound, so the changes
are mostly hardening:

- **Top handle now dismisses the panel** — added a large centered tappable handle
  region (`panel_handle_button`) with an `OnClickListener` calling the same
  dismiss routine as the X (which still works).
- **WindowManager double-add guard** — before `addView`, if the cached panel still
  has a parent from an in-flight dismiss animation, its animation is cancelled and
  it's detached first, so `addView` can't throw "already added".
- **Lifecycle teardown** — the panel is now force-removed (no animation) in
  `onInterrupt`, `onUnbind` and `onDestroy`, and the repo observer is unregistered
  in all of them, preventing a leaked overlay/observer if the service stops.
- **Per-bind allocation removed** — the `ClipAdapter` `GestureDetector` is created
  once per ViewHolder (`onCreateViewHolder`) instead of on every `onBindViewHolder`;
  positions use `getBindingAdapterPosition()` with `NO_POSITION` guards.
- **Startup load race** — the background loader only applies the on-disk list when
  the in-memory list is still empty, so a clip captured during the brief startup
  window is never clobbered by the load.
- **Shizuku cleanup** — `stopGlobalCapture` unregisters the listener reflectively,
  stops the poll thread, and clears `lastText`; removed an unused local; null-guard
  on `appContext` before posting; null-guard `ctx` in `startGlobalCapture`.
- **Adapter `Context` leak** — the previously `static SharedPreferences setting`
  field was already removed in the perf pass (settings cached per-instance via the
  application context); confirmed no static `Context` remains.
- **Manifest audit** — every component has an explicit `android:exported` (Android
  12+ requirement): launcher + PROCESS_TEXT activities exported, IME/accessibility
  exported with their bind permissions, monitor service not exported, Shizuku
  provider exported per spec; `EasyClipApp` registered; permission set complete and
  minimal.
- **Streams** — all disk I/O uses try-with-resources.
- **Verified-correct (no change needed)**: dedupe/sentinel/sort and the
  history-cap + pinned-protection logic (faithful port), the PROCESS_TEXT result
  contract, and the in-app double-tap-to-expand surface.
- **Noted, not changed (risk):** `AccessibilityNodeInfo.recycle()` is intentionally
  not called — it's deprecated/no-op on API 33+ and double-recycle crashes; the
  framework manages these nodes on current targets.
