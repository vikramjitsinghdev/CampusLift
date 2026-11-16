# Campus Lift - CS2063 Android prototype

Campus Lift is a native Kotlin/XML app for planning a UNB SafeRide trip. Choose a pickup, destination and date, find available departures, book a seat and view your ride history. Your Ride includes student and driver check-in for a one-phone demo. Open this repository root in Android Studio. There is one Android app module (`com.campuslift.app`).

## Build and run

1. Install Android SDK Platform 35 and use a complete JDK 17 or 21 (one that includes `jlink`).
2. Let Android Studio write `sdk.dir` into `local.properties`.
3. Add `ORS_API_KEY=your_key` to `local.properties` and rebuild to get live routes.
4. Run on API 26+.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest   # needs a running emulator or device
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

**API key safety.** `local.properties` is listed in `.gitignore` and is not tracked, so the key is never committed. Each teammate adds their own key. The key is compiled into debug builds only; release builds contain an empty key. A debug APK built with a key still contains it, so do not share that APK publicly.

## Screens

| Screen | File | What it does |
| --- | --- | --- |
| Home | `MainActivity` | Pickup and destination dropdowns, calendar date, **Plan Ride**, list of available times, route |
| Your Ride | `RideDetailsActivity` | Opens right after booking. Status, date and time, driver, vehicle, route map, **Cancel Ride** |
| My Rides | `MyRidesActivity` | Whole booking history in three tabs: **Active**, **Completed**, **Cancelled**. Tap a ride to open Your Ride |

## Demo

1. Choose **Student Union Building (SUB)** and **Regent Mall**.
2. Tap the **Date** field and pick a day in the calendar (today up to 30 days ahead; past days are greyed out).
3. Tap **Plan Ride**. "Available times" lists every departure still open on that date with seats left.
4. Tap **Book Ride**. The **Your Ride** screen opens with the timing, driver and the route map.
5. Tap **My Rides** to see it under Active. Cancel it from Your Ride and it moves to Cancelled. A ride moves to Completed 30 minutes after its departure.

To demo boarding on one phone, open an active booking and tap **Student: Show QR**. Take a screenshot, close the dialog, then tap **Driver: Scan student QR** and choose that screenshot with **Choose QR image**. Repeat with **Driver: Show QR** and **Student: Scan driver QR**. Both scan actions also offer a camera for a code displayed externally. After the second scan, simulated tracking runs for 30 seconds. Tap **End demo ride** when it arrives to move the booking to Completed. These demo codes expire in two minutes and the app does not authenticate either role.

With an ORS key and network the route is a live OpenStreetMap map with distance and driving time. Without them the app shows the supplied route screenshot for the trip. Booking never depends on routing.

## Locations and schedule

| ID | Location | Role | Latitude | Longitude | Source (OpenStreetMap) |
| --- | --- | --- | --- | --- | --- |
| 4 | Student Union Building (SUB) | Pickup | 45.94521 | -66.64149 | way 35143149 |
| 3 | J. Currie Center | Pickup | 45.94645 | -66.63803 | node 12716943425 |
| 1 | College Hill Road | Pickup | 45.93999 | -66.64728 | way 396583238 (a point on the road) |
| 2 | Head Hall | Destination | 45.94964 | -66.64195 | way 111404729 |
| 6 | Regent Mall | Destination | 45.93531 | -66.66455 | way 35061852 |
| 5 | Downtown (Kings Place) | Destination | 45.96138 | -66.64326 | way 34964188 |

The main building of each place is the accepted pickup or drop-off spot for this prototype (team decision, 2026-11-16).

`rides.json` has 30 departures covering all nine pickup/destination pairs. `drivers.json` has 10 demo drivers with a vehicle color and model, demo plate, and phone number. Your Ride displays these details so students can identify the vehicle. The app loads its seed from `app/src/main/assets/`.

## How the data works

```text
assets/*.json -> JsonSeeder (validate + hash) -> Room
Room RideDao  -> dropdowns, available times for a date, book, cancel, booking history
MainViewModel -> Home state (survives rotation) -> XML + RecyclerView
Room coordinates -> OpenRouteService client -> osmdroid map (or screenshot fallback)
```

- **Daily schedule, dated bookings.** A ride repeats every day at its time. A booking is for one date (`serviceDate`), so seats are counted per day. See `data/ScheduleTime.kt`.
- **Booking rules** run in one Room transaction: ride exists, that departure has not left, no duplicate booking, seats left, and at most four passengers per driver per departure.
- **Ride states.** Cancelled bookings are kept. For a booking without a started demo, history classifies it as Completed about 30 minutes after departure. An in-progress demo stays Active until **End demo ride**.
- **Seed changes apply automatically.** The app stores a hash of the three JSON files. When they change, the tables are updated on the next start. Bookings are kept, except bookings for rides that were removed or changed.
- **Database version 3** stores vehicle plates and demo ride progress. Room migrations preserve existing bookings. Schemas are exported to `app/schemas/`.
- **One demo student.** There are no accounts. Every booking belongs to `DemoStudent.ID`, defined once in `data/Entities.kt`.

## Differences from the proposal and Status Update 1

| Planned | Built | Why |
| --- | --- | --- |
| Firebase | Room (local database) | Course local-storage requirement |
| Google Maps | osmdroid + OpenStreetMap | No Maps key or billing needed. osmdroid's upstream is archived, so revisit before any real release |
| Pickups: SUB, Sir James Dunn Hall, Head Hall/IUC | Pickups: SUB, J. Currie Center, College Hill Road | Matches the team's nine route screenshots |
| Typed destination | Destination dropdown | Fixed destinations for this prototype; see next prototype below |

## Next prototype

- **Searchable destinations.** Let the student search for any destination instead of choosing from the fixed list (requested 2026-11-16).
- Location permission and geofencing, notifications/reminders, and pickup map with current location.

There is no authentication, payment, dispatch or live tracking.

## Tests

- JVM: `RouteModelsTest`, `RoutePreviewsTest`, `ScheduleTimeTest`.
- Emulator: `DatabaseTest` (seed, available times per date, booking rules, history states, concurrency, seed refresh, persistence), `MigrationTest` (version 1 to 2), `RideFlowTest` (date, search, book, Your Ride opens, cancel, My Rides tabs, landscape).

See `VERIFICATION.md` for what was actually run and what was not.
