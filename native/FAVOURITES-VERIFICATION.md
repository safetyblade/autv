# UI verification and Favourites release status

Baseline inspected: main `53f7e0e704a4dfbe3df93cef5719204d9518422d`.
PR #14 and PR #19 are merged. Subsequent PlayerScreen/TvDualGuide accessibility
changes are retained. This feature does not alter PlaybackModel or player engines.

| Previous UI requirement | Code on main | Verification here |
| --- | --- | --- |
| Quick Guide and Full EPG accessible | Yes, Guide/Menu and header action | Source inspection; instrumentation compiled, not run |
| First Down/Up reveals the complete next row | Yes, GuideViewport + immediate lazy-list scroll request | Boundary JVM tests pass; Compose layout tests await Android |
| Rapid navigation/reversals/focus retention | Yes, mutable index + pending-scroll replacement + one guide focus owner | JVM geometry tests pass; 651-row Compose traversal awaits Android |
| Channel/current programme immediately tunes | Yes, shared PlaybackModel.request path; Full EPG enables one-shot recovery | Compose callback and Media3 recovery tests compiled, not run |
| Future/past programme opens details only | Yes, actual airing interval gates programme actions | Timeline JVM tests pass; Compose tests await Android |
| Missing EPG does not hide or prevent known-stream tuning | Yes | JVM schedule tests pass; Compose selection awaits Android |
| Failed Full EPG tune resumes previous channel | Yes, GuideTuneRecovery uses existing request path | Recovery JVM tests pass; retained Media3 test awaits Android |
| Quick Guide and Back/CH/numbers/control mappings retained | Present | Existing regression sources retained; instrumentation awaits Android |

Favourites adds deferred short-OK release handling, long-OK suppression and local
membership. No prior guide defect required an additional playback correction.
A failed Full EPG tune resumes the old stream through the single player and can
briefly interrupt it; this is the existing main behaviour, not a seamless guarantee.

## Executed checks

- Cached Kotlin/Compose compilation of app, core sources and relevant Android
  instrumentation sources: successful. Existing Cast API deprecation warning only.
- Cached Kotlin/JUnit core-data/core-guide suite: **62 tests passed**, including
  13 new identity and remote gesture tests; malformed XML is an intentional fixture.
- Existing native/tools Python suite: **8 tests passed**.
- Gradle unit/build/connected instrumentation command attempted: cannot start
  `FileLockContentionHandler` (`Could not determine a usable wildcard IP`).
  This environment failure occurs before any project compilation or APK packaging.
- Compose/Android instrumentation: **not executed**. Added cases cover playback,
  Quick Guide, Full EPG, long flag/no-repeat timer/key-up, next short OK, persistence,
  unavailable Favourites, EPG/metadata refresh, and phone touch. Existing UI/recovery
  tests remain in the suite. Compilation alone is not a passing runtime test.
- Android TV / Google TV remote or physical device: **not tested**. ADB cannot
  initialize its device service in this restricted workspace. No physical target
  is connected through the available environment capabilities.
- Updated APK: **not produced; not ready for installation**. Run the repository's
  existing Native app workflow on the Favourites branch or build `:app:assembleDebug`
  in an Android-capable environment. The expected local artifact is
  `android/app/build/outputs/apk/debug/app-debug.apk` (do not substitute an old APK).

## Required Android validation

Run `gradle -p android :core-data:testDebugUnitTest :core-guide:testDebugUnitTest
:app:assembleDebug :app:connectedDebugAndroidTest`. On actual remotes check short
and long OK (with and without repeat/long flags), including hidden playback chrome,
focused CH/Guide controls, key-up suppression, digits followed by OK, cancellation
on another key/window loss, rapid guide navigation, complete row alignment, Back,
real failed-tune rollback and playback continuity during metadata/EPG refresh.
Verify Favourites after process restart and refresh, including unavailable channels.
Channels 203/297 guide fixtures do not establish resolution of their playback faults.

No stream URLs, catalogue numbers/order, M3U/XMLTV/guide generation, source resolvers,
playlist files or six-hour refresh workflow are changed. No dependency added.
