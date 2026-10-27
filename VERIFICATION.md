# CampusLift verification — 2026-10-26

## Actual results

| Check | Result |
| --- | --- |
| Gradle 8.14.3 distribution download/bootstrap | Passed |
| Android/Kotlin plugin resolution and Gradle project configuration | Passed |
| Standard Gradle wrapper generation | `BUILD SUCCESSFUL` (`wrapper` task only) |
| `gradlew.bat assembleDebug` | Failed: Android SDK location not found |
| Re-run `gradlew.bat assembleDebug testDebugUnitTest --console=plain` using generated wrapper | Same missing SDK blocker; compilation/tests did not run |
| XML syntax, string-resource references, package declarations, runtime seed counts/IDs, original JSON preservation | Passed (`.verification/verify.py`) |
| Actual DAO SQL: location lists, search joins, remaining seats, duplicate-count query, driver-load query, cancellation ownership, active My Rides filtering, missing ride lookup | Passed against Python's SQLite engine; this is **not** a Room runtime test |
| Kotlin route-model compilation | Passed using installed Kotlin compiler |
| Kotlin route-model assertion self-check | Passed: summary values, longitude/latitude order, empty routes, missing summary, invalid geometry |
| `RouteModelsTest` JUnit suite | Added; not executed because Android Gradle test task needs SDK |
| `DatabaseTest` Android Room/instrumentation suite | Added; not executed; needs SDK and emulator/device |
| Full Android source compilation / Room code generation / APK | Not verified; SDK blocker occurs before compilation |
| Live ORS request, map tiles, Android navigation, booking UI/persistence | Not runtime-tested |
| Emulator | Not run |

The build used the installed IntelliJ bundled JDK and a workspace-local Gradle cache. Gradle's Android analytics also reported that the user-home analytics configuration was unwritable; this was nonfatal. The fatal build error was the missing Android SDK. No SDK location was fabricated and no APK is claimed.

## Scope and preserved data

- Native Kotlin, Android XML, Room, Gson, platform HTTPS client, osmdroid, Gradle Kotlin DSL.
- Original `Data/` Kotlin and JSON files are untouched. Runtime entities/DAO/database adapt the supplied architecture and preserve the four-passenger driver/time check.
- Runtime JSON has six locations, four drivers, eight rides. Driver/ride assets are byte-for-byte copies. Location IDs/names are preserved; coordinates added only for sourced Head Hall and Regent Mall building positions.
- Four coordinates still need verification: College Hill Road, J. Currie Center, Student Union, Downtown. Broad locations also need a specific meeting point chosen by the team.
- ORS key is blank in ignored `local.properties`. Local booking is independent of routing success. A development key is embedded in the APK when configured; do not distribute a valuable unrestricted key.
- No authentication, driver accounts, payment, dispatch, emergency integration, live fleet tracking, or navigation system.

## Remaining manual verification

1. Open this root folder in Android Studio. Install SDK Platform 35 / Build Tools 34.0.0 and let Android Studio write `sdk.dir` to `local.properties`.
2. Add `ORS_API_KEY=...` to `local.properties`; rebuild. Use JDK 17 or 21 for Gradle.
3. Run `assembleDebug`, `testDebugUnitTest`, then `connectedDebugAndroidTest` with an emulator.
4. Demo Head Hall → Regent Mall (17:00): inspect ORS distance/time, road snapping, markers, tiles, and geometry. These building positions are not verified pickup entrances.
5. Book, verify duplicate rejection, open My Rides, restart the app to verify persistence, cancel, return home to verify restored availability, then rebook.
6. Verify empty searches, missing key, missing coordinates, and offline routing while confirming local bookings still function.
7. Add remaining verified coordinates; clear demo app storage to re-seed after asset changes (this removes local bookings).

## Obsidian update

All four CampusLift Markdown notes were read recursively before implementation. The canonical `CampusLift Current State.md` update was attempted after implementation, but **the workspace's approval settings rejected writing outside the project directory**. No Obsidian note was changed. This file records the verified implementation status until that note can be updated in a session with vault write access.

Suggested concise memory update: native Kotlin/XML prototype and Room/JSON/booking/ORS/map code are implemented; Gradle bootstrap/configuration and local structural/SQL/route-model checks passed; Android build is blocked by an unconfigured SDK; runtime remains unverified; ORS key and four location coordinates are missing. Original `Data/` files are preserved. Next milestone is SDK setup, successful Android build/tests, then emulator demonstration.
