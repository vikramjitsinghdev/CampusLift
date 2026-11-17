# Campus Lift verification

## Phase One time picker and ride flow

- Debug APK built. All 16 unit tests passed, and Android lint reported zero errors.
- On the API 37 emulator, 23 tests passed in the full run, including tapping the time dropdown, checking a disabled row, confirming a booking, and verifying that an overnight ride books on its actual departure date. The remaining QR test passed separately on a dedicated read-only emulator after correcting the photo-picker test setup.
- The QR test captured both displayed codes, supplied those screenshots to the app as picker results, and verified that the simulator reached the destination and the ended booking appeared under Completed.
- Physical camera scanning, TalkBack, and a physical phone run are still unverified.

## Earlier reliability fixes, November 16, 2026

Everything under "Verified" was actually run on this date after implementing F01–F07.

## Verified

Environment: Windows, Gradle 8.14.3 wrapper, normal `app/build` output folder, emulator `Medium_Phone_API_37.0(AVD) - 17` (`emulator-5554`).

| Command | Result |
| --- | --- |
| `.\gradlew.bat --no-daemon assembleDebug` | BUILD SUCCESSFUL |
| `.\gradlew.bat --no-daemon testDebugUnitTest` | 15 tests, 0 failures (`ScheduleTimeTest` 7, `RouteModelsTest` 4, `RoutePreviewsTest` 2, `DemoRideTest` 2) |
| `.\gradlew.bat --no-daemon lintDebug` | BUILD SUCCESSFUL (0 errors, 29 warnings; API-27 `windowLightNavigationBar` error resolved) |
| `.\gradlew.bat --no-daemon connectedDebugAndroidTest` | 22 tests, 0 failures, 0 skipped (`DatabaseTest` 12, `MigrationTest` 2, `MainViewModelTest` 4, `RideDetailsRouteTest` 2, `RideFlowTest` 2) |

What the tests cover:

- **F01 & F07 (`MainViewModelTest`)**: delayed/out-of-order date responses are dropped, old cards clear immediately on `setDate()`, stale cards are rejected in `book()`, saved bookings still open Ride Details with `book_success` even if post-book refresh throws, and search/booking failures use distinct student-friendly messages.
- **F02 (`DatabaseTest`, `MigrationTest`)**: schema upgraded from v3 to v4 (`app/schemas/com.campuslift.app.data.AppDatabase/4.json`), v1→v4 and v3→v4 + `syncSeed()` preserve and backfill booking snapshots (`pickupId`, `destinationId`, `pickup`, `destination`, `driverName`, `carModel`, `phone`, `plate`, `departureTime`), edited/removed seed rides retire without deleting bookings, and seat reductions below existing bookings stop new sales without touching existing bookings.
- **F03 (`lintDebug`)**: `windowLightNavigationBar` moved to `values-v27/themes.xml`; 0 lint errors.
- **F04 (`DatabaseTest`)**: `cancelBooking` enforces the 30-minute post-departure active window inside the Room transaction (including across midnight) and preserves student ownership checks.
- **F05 (`ScheduleTimeTest`)**: `ScheduleTime` evaluates consistently in `America/Halifax` across device timezones (`UTC`, `Asia/Tokyo`, `America/Halifax`), across midnight and DST transitions, and at the 30-minute active/completed boundary.
- **F06 (`RideDetailsRouteTest`)**: offline route fallback shows `retryRoute`, repeated taps while loading do not start duplicate requests, retry success hides fallback preview and draws one live route (clearing old overlays), and missing key shows fallback without retry or developer text.
- **Core UI & DB (`DatabaseTest`, `RideFlowTest`)**: available times per date, booking rules, concurrency, history tabs (`Active`, `Completed`, `Cancelled`), Ride Details navigation, cancellation, and landscape home screen.

## Not verified

- Physical devices, API 26 emulator/device system-bar appearance, large fonts, and full accessibility audit (F08).
- App launcher icon, `drawable-nodpi` move for route previews, and remaining lint warnings disposition (F09).
- Release build (`assembleRelease`) and live manual UI walkthrough after the F01–F07 edits.

## Notes

- Edits were made directly in the current working tree; nothing was committed or pushed.
