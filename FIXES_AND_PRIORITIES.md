# CampusLift: fixes and priorities

Prepared: November 16, 2026  
Status: proposed work; no fixes implemented by this document

## 1. Scope and overall assessment

This report covers the current CampusLift project folder, including its uncommitted files. It excludes `CampusLift-qr-changes.zip` and all proposed QR changes. References below are relative to this project root.

CampusLift has a sensible foundation for a CS2063 Android prototype: Kotlin application logic, XML screens, JSON seed data, Room persistence, and OpenRouteService routing. The main screens support planning a dated ride, booking, viewing details, cancellation, and history. Booking checks run in a database transaction; routing failure does not prevent booking.

The immediate goal should be reliable booking and preserved history, followed by compatibility and usability. A rewrite is unnecessary. Keep the existing database, DAO, ViewModel, layouts, and tests wherever practical.

This is a local demonstration for one demo student. Bookings and seat counts are not shared between phones. That is a scope limitation, not a defect to solve by adding authentication or a backend during this fixes pass.

## 2. What was actually verified

During the project review in this chat:

- Inspected application logic, database queries, seed handling, route handling, layouts, configuration, and test sources.
- Debug APK assembly succeeded using the existing default Gradle cache.
- The unit-test task was accepted as up to date. Its existing reports contain 10 tests and zero failures; the tests were not freshly executed during that invocation.
- Android lint ran and failed with 1 error and 16 warnings. The error concerns an API-27 theme attribute in the API-26 base theme.
- Earlier attempts were blocked by Gradle daemon access and a missing plugin in a different local cache. These were environment problems, not evidence of an application compiler defect.
- No application source was changed during the review.

The repository's `VERIFICATION.md` records an earlier run of 13 emulator tests and a manual live-route check. Those are historical results, not fresh verification from this review. Emulator flows, physical devices, live routes, accessibility, and release behavior were not retested here.

The behavioral findings below are supported by source inspection. Timing-related scenarios still need controlled reproduction tests; they are not claimed as observed UI failures.

## 3. Exact priority and execution order

**P1 — protect booking correctness and data:** fix before adding features or relying on saved reservations.

1. F01: prevent wrong-date bookings from stale search results.
2. F02: preserve bookings when the seed schedule changes.

**P2 — complete the reliability pass:** fix before the next stable demo handoff.

3. F03: resolve the lint compatibility error. This is a small independent fix and can be completed first without changing its severity.
4. F04: enforce cancellation eligibility at the time of cancellation and refresh time-sensitive screens.
5. F05: use the campus timezone consistently.
6. F06: let users retry a failed route from Ride Details.

**P3 — usability and cleanup:** follow the reliability pass.

7. F07: improve student-facing error messages.
8. F08: address accessibility and layout verification gaps.
9. F09: add the app icon and triage remaining lint warnings.

Complete each fix with its focused tests. Do not postpone all verification until the end. Features such as searchable destinations, reminders, live tracking, or QR check-in are separate work after these fixes.

## 4. Detailed fixes

### F01 — stale results can book the wrong date

**Priority:** P1. **Evidence:** source-confirmed race and stale-state paths; not reproduced on a device in this review.

**Problem:** `setDate()` changes the selected date immediately, while existing ride cards remain available until an asynchronous refresh finishes. `refresh()` skips work when a search is already running. `refreshRides()` only checks pickup and destination before accepting its result; it does not check the requested date. `book()` books the date attached to the card, which may differ from the displayed results heading.

**Example:** results are shown for tomorrow. The student selects the following day and taps an old card before the refresh completes. The booking can still be made for tomorrow. An older response can also overwrite newer results.

**Required fix:** capture pickup, destination, and date as one request. Cancel or invalidate superseded requests, and accept results only when the complete selection still matches. Immediately clear or disable old cards after a date change. Ensure changes during an active search trigger a replacement query. At the booking entry point, reject a card that no longer belongs to the current selection.

**Files and callers:** `app/src/main/java/com/campuslift/app/ui/MainViewModel.kt` (`setDate`, `search`, `refresh`, `refreshRides`, `book`); `MainActivity.kt` and `ui/RideAdapter.kt` for loading/button state. Keep routing independent from booking readiness.

**Acceptance checks:** deliberately delay the first query, change dates twice, and deliver responses out of order. Only the latest date's cards may appear. An old card cannot book during refresh. Changing date during an initial search must eventually show the new date. Returning from a cancelled booking must still refresh seat counts correctly.

### F02 — seed updates delete booking history

**Priority:** P1. **Evidence:** confirmed implementation and an existing test that expects deletion.

**Problem:** `RideDao.applySeed()` deletes every booking for a removed or changed ride. Equality includes seat count, so even a capacity edit deletes associated bookings. Completed and cancelled records disappear along with future reservations. History also joins mutable driver/location records, so changing those can alter how past bookings are displayed.

**Required fix:** stop using deletion as the default response to schedule changes. Preserve a booked trip's historical details. A practical approach is to retain booked schedule versions and retire them from new searches, introducing a new version for changed departures. Another approach is storing sufficient booking snapshots. Select the smallest design that preserves history and prevents an existing reservation from silently changing meaning.

For removed or materially changed future trips, keep the reservation record and communicate that it is no longer available; do not silently erase it. Decide and document the replacement/cancellation behavior before changing the schema. Reducing capacity below existing reservations must preserve those reservations and stop further sales rather than delete passengers.

**Files and callers:** `data/RideDao.kt`, `data/Entities.kt`, `data/AppDatabase.kt`, seed synchronization, history/detail queries, and any affected status display. Update exported Room schemas, `DatabaseTest.kt`, `MigrationTest.kt`, `README.md`, and `VERIFICATION.md` if the schema or documented behavior changes.

**Acceptance checks:** create future, completed, and cancelled bookings; change a departure time, reduce seats, remove a ride, and edit a driver/location. History remains present and understandable. Retired trips cannot receive new bookings. An unchanged seed remains harmless. Test a real upgrade followed by `syncSeed()`, not just the migration in isolation.

**Caution:** a fix cannot recover records already deleted unless a separate copy exists. Never use destructive migration to implement this change.

### F03 — Android lint fails on the base theme

**Priority:** P2. **Evidence:** lint failure observed during this review.

**Problem:** `app/src/main/res/values/themes.xml:10` sets `android:windowLightNavigationBar`, which requires API 27, while `minSdk` is 26.

**Required fix:** put the attribute in a suitable `values-v27` theme override while retaining an API-26-compatible base theme. Preserve the shared theme styling. Do not raise the minimum Android version solely to hide this error or suppress the error without addressing compatibility.

**Acceptance checks:** `lintDebug` no longer reports this error; debug assembly succeeds; verify system-bar appearance and content insets on API 26 and a newer Android version.

### F04 — stale screens allow cancellation after completion

**Priority:** P2. **Evidence:** confirmed query/UI behavior; boundary flow needs a reproduction test.

**Problem:** completion is calculated from the clock; it is not stored as a new booking status. A completed booking can therefore remain `confirmed` in Room. The cancellation query checks ownership and stored status, but not time. If Ride Details remains open past the completion boundary, its old Cancel button can still cancel the completed booking. My Rides also refreshes on entry rather than continuously as time passes.

**Required fix:** enforce the allowed cancellation window in the database transaction using a fresh time value. Preserve the student ownership check. Refresh or invalidate time-sensitive UI at relevant boundaries while visible, and refresh on return. Hiding a button alone is insufficient.

**Policy distinction:** at minimum, reject cancellation after the current active window ends. Whether cancellation should instead stop at departure is a separate product decision; do not silently change that rule.

**Files and callers:** `data/RideDao.kt` (`cancelBooking`), `data/ScheduleTime.kt`, `RideDetailsActivity.kt`, `MyRidesActivity.kt`, and cancellation tests.

**Acceptance checks:** cancellation succeeds inside the agreed window and fails outside it, even from an already-open screen. Completed history does not move to Cancelled. Another student still cannot cancel the booking. Verify midnight and the exact cutoff boundary using a controlled clock.

### F05 — phone timezone changes campus availability

**Priority:** P2. **Evidence:** source uses device-local date/time.

**Problem:** `ScheduleTime.now()` uses `LocalDateTime.now()`. The calendar and relative date labels also use device-local dates. A phone in another timezone can hide a Fredericton departure too soon, permit booking after its actual departure, or assign the wrong history category.

**Required fix:** interpret the schedule consistently in `America/Halifax`. Use the same time source for availability, booking validation, completion, calendar limits, and Today/Tomorrow labels. Keep the date picker's UTC timestamp encoding separate from the campus-time business rules. Make the clock controllable in tests without introducing a large abstraction.

**Files and callers:** `data/ScheduleTime.kt`, `ui/DepartureText.kt`, `MainActivity.kt` date picker, and callers that supply the current time to DAO operations.

**Acceptance checks:** changing the device timezone does not change which campus departures are bookable at the same instant. Test near midnight and daylight-saving transitions. Labels, calendar limits, and stored service dates agree.

### F06 — a failed detail route cannot be retried in place

**Priority:** P2. **Evidence:** `RideDetailsActivity.show()` requests the route only while `routeJob == null`; a finished failed job stays assigned.

**Problem:** after an offline request shows the fallback image, returning with connectivity does not request a new route on that screen. The student must recreate the screen to try again.

**Required fix:** add an explicit retry for route failure, track loading separately from a completed job, and prevent duplicate requests. On success, replace the fallback with the live map and clear old overlays. Preserve the fallback while offline and keep cancellation and booking independent of routing.

**Files:** `RideDetailsActivity.kt`, `res/layout/activity_ride_details.xml`, and `res/values/strings.xml`.

**Acceptance checks:** open offline, see a labelled fallback, restore connectivity, retry, and see one live route. Repeated taps do not start duplicate requests. Missing configuration produces an understandable result without an endless spinner.

### F07 — errors speak to developers instead of students

**Priority:** P3. **Evidence:** current string resources.

**Problem:** errors tell students to edit seed files, set `ORS_API_KEY`, or rebuild. A failed search can use an error about updating a booking. These messages do not tell the student what they can reasonably do next.

**Required fix:** distinguish loading, searching, booking, and routing failures. Give a simple next action: retry, select another ride, or use the preview. Keep configuration instructions in developer documentation or an appropriate debug-only surface. Do not log keys or unnecessarily expose booking details.

**Files:** `res/values/strings.xml`, `MainActivity.kt`, `ui/MainViewModel.kt`, and `RideDetailsActivity.kt`.

**Acceptance checks:** trigger each failure category and verify the message matches the operation, offers an available action, and does not imply that a successfully saved booking failed because a later refresh failed.

### F08 — accessibility and device coverage remain unverified

**Priority:** P3, raised if testing finds an unusable core flow. **Evidence:** verification gap, not a claim that all these cases fail.

**Required work:** exercise the current layouts with large fonts, screen-reader navigation, landscape orientation, and a small display. Check tab labels with counts, button reachability, reading order, map/preview descriptions, loading announcements, contrast, and touch targets. Test the oldest supported API and a newer API. Fix observed failures rather than redesigning every screen speculatively.

**Files:** the three activity layouts, `item_ride.xml`, theme/colors, and `ui/SystemBars.kt` as needed.

**Acceptance checks:** a student can choose a date, search, book, inspect details, cancel, and find history without clipped essential controls or reliance on the map image alone. Record tested devices/settings and unresolved limitations in `VERIFICATION.md`.

### F09 — app identity and remaining lint warnings

**Priority:** P3. **Evidence:** generated lint report.

**Required work:** add an application icon; put route preview bitmaps in an appropriate density folder, normally `drawable-nodpi` for screenshots; remove the unused preview background if it is still unused. Review duplicate background drawing as a low-priority cleanup.

The report also flags the target API and backup/device-transfer configuration. Evaluate these before distribution, with current requirements checked at that time. Preserve the intent to protect local booking data and test any configuration change. Avoid broad dependency or SDK upgrades merely to clean up the report.

**Acceptance checks:** launcher icon displays correctly; previews retain suitable size and sharpness; lint has no unresolved errors; remaining warnings have a documented disposition. Confirm release builds still omit the developer ORS key and show the expected route fallback.

## 5. Regression and handoff checklist

Run checks appropriate to each change, then complete this final pass:

- [ ] F01: delayed/out-of-order date search responses cannot create a wrong-date booking.
- [ ] F02: schedule edits and upgrades preserve historical and affected future booking records.
- [ ] F03: API-26 compatibility error resolved.
- [ ] F04: cancellation respects the current cutoff and ownership, including stale screens.
- [ ] F05: date/time decisions remain consistent across device timezones.
- [ ] F06: route fallback can recover through retry without affecting bookings.
- [ ] Existing duplicate, capacity, concurrency, persistence, and per-date booking tests still pass.
- [ ] `gradlew.bat assembleDebug` succeeds.
- [ ] `gradlew.bat testDebugUnitTest` succeeds; record whether tests executed or were up to date.
- [ ] `gradlew.bat lintDebug` succeeds with remaining warnings reviewed.
- [ ] `gradlew.bat connectedDebugAndroidTest` succeeds on a running test device/emulator.
- [ ] Manually exercise calendar selection, booking, detail navigation, cancellation, all history tabs, and offline routing.
- [ ] For schema changes, verify existing-user migration and subsequent seed synchronization together.
- [ ] Document actual results, environment, and remaining limits in `VERIFICATION.md`.

Use a test installation/database for destructive test fixtures. Existing instrumented tests may clear or modify bookings; do not run them against a user's only copy of meaningful data.

## 6. Constraints and deferred work

Keep Kotlin, native Android layouts, Room, JSON seeds, and OpenRouteService. Do not introduce Firebase, authentication, payments, dispatch, or a backend as part of these repairs. Preserve debug-only ORS key configuration and keep secrets out of version control.

Searchable destinations and other roadmap features remain separate requests. This report does not import QR statuses, scanner behavior, dependencies, or files from the excluded ZIP. It also does not claim the current demo is suitable for real multi-user reservations.

The recommended completion point is a demonstrably reliable local prototype: correct date selection, preserved booking records, enforced cancellation rules, consistent campus time, recoverable routing, and recorded passing checks. Production deployment would require a separate scope and review.
