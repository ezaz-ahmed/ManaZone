# Manazone

A minimal AMOLED focus timer for Android. Tap a duration, focus, done.

- Home: today's focused time, completed sessions, and your duration presets.
- Focus: a large countdown on pure black. The screen stays awake. **Stop** ends the session right away.
- Settings: manage presets, completion vibration and sound, and optional Do Not Disturb.
- 1×1 home screen widgets: each one is bound to a duration and starts it with one tap.

## Build, install, run

Requirements: JDK 17+ (the project was built with JDK 25), Android SDK platform 37, and a device or emulator running Android 11 (API 30) or newer.

```sh
./gradlew :app:assembleDebug          # build
./gradlew :app:installDebug           # install on the connected device/emulator
adb shell am start -n dev.ezaz.manazone/.MainActivity
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Checks:

```sh
./gradlew :app:testDebugUnitTest              # JVM unit tests (session logic, stats, presets, DND policy)
./gradlew :app:lintDebug                      # Android lint
./gradlew :app:connectedDebugAndroidTest      # instrumented tests (needs a device/emulator)
./gradlew :app:assembleRelease                # R8-minified release APK (unsigned)
```

The instrumented tests run `cmd notification allow_dnd/disallow_dnd/set_dnd` and `appwidget grantbind` on the device. They clear Manazone's data.

## Architecture

The app is a single module with one activity. The packages are grouped by concern:

| Package | Responsibility |
|---|---|
| `core` | `TimeSource`: wall clock, `elapsedRealtime`, and boot count, injectable for tests |
| `session` | `FocusSession` + `SessionTiming` (pure math), `SessionManager` (the only place transitions happen), `DailyStats` |
| `data` | Room entity/DAO and `RoomSessionStore` (implements `SessionStore`) |
| `presets` | `DurationParser`, `PresetRules` (validation and list edits), and `PreferencesRepository` (DataStore) |
| `dnd` | `DndPolicy` (pure decisions) and `AndroidDndController` (platform wrapper) |
| `alarm` | `CompletionAlarmScheduler` + `CompletionReceiver`: wakes the app at the planned end |
| `feedback` | `CompletionFeedback`: vibration and a synthesized "ding-dong" (no audio assets) |
| `widget` | Glance `FocusWidget`, its receiver, and `WidgetConfigActivity` |
| `ui` | Compose `Home` / `Focus` / `Settings` screens and `MainViewModel` (simple route state, no nav library) |

`ManazoneApp` wires everything together by hand in `AppContainer`.

### Single source of truth

The Room `sessions` table is the only session state. The UI, the widgets and the alarm all read it, and every change goes through `SessionManager`. No component runs its own timer. The countdown is recomputed from persisted timestamps every second, so if the redraw loop stops, accuracy is unaffected.

### Timing model

Each session stores `startWallMillis`, `plannedMillis`, and a monotonic anchor (`anchorElapsedMillis` + `anchorBootCount`).

- **Same boot:** elapsed time is `elapsedRealtime − anchor`. This clock is monotonic and keeps counting during deep sleep, so changes to the wall clock or time zone don't affect the countdown.
- **After a reboot** (detected from `Settings.Global.BOOT_COUNT`, or, if that's unavailable, from `elapsedRealtime` going backwards): elapsed time falls back to `wall − startWall`, clamped to 0. The session is then re-anchored to the new boot so later wall-clock changes no longer matter.
- **Remaining** = planned − elapsed. **Focused** = min(elapsed, planned).

### Lifecycle rules

| Event | Behavior |
|---|---|
| Start (preset or widget) | One row is inserted in a transaction that fails if a session is already active. A mutex serializes starts, and a second start returns the running session. |
| Stop | Immediate, with no dialog. The session is saved as `CANCELLED` with the actual elapsed time. If the plan had already run out, it is recorded as completed instead. |
| Call / app switch / lock / rotation | Nothing changes. The session keeps running and the correct time is shown on return. Leaving the app is never treated as Stop. System Back on the focus screen goes to Home, and a "Return" banner leads back. |
| Completion | `UPDATE … WHERE status='ACTIVE'` only succeeds for one caller. That caller saves the full planned time as `COMPLETED`, restores DND, then plays the cue. Repeated calls from the UI tick, the alarm, or app start are no-ops, so the cue plays exactly once. |
| Completed in background | The `ELAPSED_REALTIME_WAKEUP` alarm finalizes the session and plays the cue without opening the activity. The completion screen appears the next time you open the app. |
| Process death | On app start, `reconcile()` restores a live session (and re-arms the alarm) or finalizes an expired one exactly once. Cancelled and completed rows are never reactivated. |
| Reboot | No boot receiver is used. On the next app start, the session either continues using the wall-clock time that passed, or is finalized as completed if it ran out. |

The completion cue only plays if finalization happens within 2 minutes of the planned end. A session finalized later than that (for example, after a reboot or a long-dead process) is recorded silently, and the completion state is shown when you open the app.

### Accounting

Durations are stored in milliseconds and shown as whole minutes, rounded down. Daily totals use the device's local calendar day. A session counts over the interval `[start, start + focused]`, so one that crosses midnight is split between the two days. A completed session counts toward the day it ended. Cancelled sessions add their actual focused time but are never counted as completed.

### Do Not Disturb

DND is off by default. When you turn it on, Manazone explains what it does, then opens the system access screen. During a session, Manazone switches to *priority* mode only if DND was off. Afterwards it restores the previous mode only if the filter is still the one it set, so it won't overwrite a change you or another app made. If access has been revoked, sessions continue without DND and Settings says so. Alarms, priority contacts and emergency alerts follow the system's own DND rules. On Android 15+, the system may show this as an app-owned DND rule.

## Platform notes and limitations

- **minSdk 30 (Android 11).** This gives access to `BOOT_COUNT`, notification-policy access, and reconfigurable widgets. `targetSdk`/`compileSdk` is 37.
- **Exact alarms:** `SCHEDULE_EXACT_ALARM` is declared but never requested. It is granted automatically on API 30–32. On 33+, unless you grant it, Manazone uses an inexact alarm, so a cue for a session that completes in the background can arrive a little late. The recorded times are always exact, and while the focus screen is visible the cue plays on time.
- **Keep screen on** uses `View.keepScreenOn` while the countdown is visible. It does not stop you from locking the device manually, and it ends when you leave the app.
- **Widgets:** 1×1 with a 40dp minimum. Launchers decide the final cell size and corner shape. Reconfiguring (choosing a different duration) depends on the launcher (`widgetFeatures="reconfigurable"`, API 31+ launchers). Where it isn't supported, remove the widget and add it again. A widget keeps its own duration even if that preset is later removed. Widgets show an emerald outline while a session is running. They don't show a live countdown.
- **Calls:** no phone or call-log permissions are used. Calls simply cover the activity, and timing is unaffected.
- The completion sound uses notification-event audio, so it follows your ringer mode and DND.
- `legacy/landing-prototype/` contains the earlier View-based landing screen that was in the repo before Manazone. It is kept for reference and is not part of the build.
