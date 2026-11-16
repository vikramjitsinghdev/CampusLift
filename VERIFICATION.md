# Campus Lift verification

## One-phone demo update

- Debug app and test APKs built successfully.
- 12 JVM tests passed, including demo QR expiry and booking/role checks, and simulated progress.
- 14 Android tests passed on the API 37 emulator under a separate app ID. The tests cover the database upgrade, driver and vehicle seed data, the two check-in transitions, and moving an ended demo to ride history.
- The Android tests exercised the booking screens and demo state transitions. Camera scanning, screenshot selection, and the moving marker were not manually checked.

## Earlier calendar and ride history verification - 2026-11-16

Everything under "Verified" was actually run on this date, after the calendar, Your Ride and My Rides screens were added.

## Verified

Environment: Windows, JDK `C:\Users\User\.jdks\jbr-21.0.11`, Gradle 8.14.3 wrapper, normal `app/build` output folder, emulator `Medium_Phone_API_37.0` started headless and read-only, `ORS_API_KEY` set in `local.properties`.

| Command | Result |
| --- | --- |
| `gradlew.bat assembleDebug` | BUILD SUCCESSFUL |
| `gradlew.bat testDebugUnitTest` | 10 tests, 0 failures (ScheduleTimeTest 4, RouteModelsTest 4, RoutePreviewsTest 2) |
| `gradlew.bat connectedDebugAndroidTest` | 13 tests, 0 failures (DatabaseTest 10, MigrationTest 1, RideFlowTest 2) |
| `git check-ignore local.properties` / `git ls-files local.properties` | Ignored, not tracked |

What the tests cover:

- Available times depend on the chosen date: today hides rides that already left; tomorrow and later dates show every time; a past date shows none.
- Booking for a chosen date; each date has its own seats; past dates and departed rides are rejected.
- History: a booking is Active, becomes Completed 31 minutes after departure (still Active at 20 minutes), and Cancelled after cancelling. Cancelled bookings stay stored. Another student cannot open or cancel a booking.
- Booking rules: missing ride, duplicate, full ride, four-passenger driver limit, eight students booking four seats at once.
- Seed: 6 locations with coordinates, all nine pairs have rides, edited seed is applied, unchanged seed leaves bookings alone.
- Booking survives closing and reopening the database. Migration from version 1 to 2 keeps a booking.
- UI through the real controls: placeholder validation, date set to tomorrow, SUB to Regent Mall shows two times starting "Tomorrow 18:00", Book Ride, **Your Ride opens by itself**, shows "Ride confirmed", the departure, pickup and destination, Cancel Ride changes it to "Ride cancelled", My Rides tabs show "Active (0)", "Completed (0)", "Cancelled (1)" and the cancelled ride is listed.
- Home screen in landscape.

Manual check on the emulator (screenshots viewed):

- Calendar opens in the app colours, past days greyed out, picked Nov 18, field shows "Wed, Nov 18".
- "Available times - Wed, Nov 18" list with seats.
- Your Ride after booking with a **live OpenRouteService route**: SUB to Regent Mall, 2.9 km, 7 min, route line and both markers drawn on OpenStreetMap tiles.
- My Rides with the booking under Active (1).

## Not verified

- Live routes for the other eight pickup/destination pairs (only SUB to Regent Mall was looked at).
- The Completed tab with a real ride on the emulator. It is covered by the database test with a fixed clock, not by waiting for a real departure.
- Any Android version other than API 37 (accepted by the team for now). minSdk is 26.
- Physical devices, large fonts, accessibility audit, tablets, a release build.
- The calendar itself is tapped only in the manual check; the automated test sets the date through the ViewModel.

## Notes

- Nothing was committed or pushed.
