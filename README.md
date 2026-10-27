# CampusLift — CS2063 Android prototype

Native Kotlin/XML student app. Open **this folder** (the one containing `settings.gradle.kts`) in Android Studio. No Git setup is required. Original supplied files remain in `Data/`; only the copies under `app/` are compiled or loaded by Android.

## Setup

1. Install Android Studio with Android SDK Platform 35 and Build Tools 34.0.0. Use JDK 17 or 21 for Gradle. Android Studio's bundled JDK is suitable.
2. Let Android Studio set `sdk.dir` in `local.properties`.
3. Set `ORS_API_KEY=your_private_key` in that same file. Keep it local. Rebuild after changing the key. A missing key does not prevent compilation or local booking.
4. Sync, then run on an API 26+ device/emulator with internet access for routing/maps.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

The last command needs a running emulator/device. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Demo

Choose **Head Hall → Regent Mall**, then Find Ride. This supplied ride departs at 17:00 with Daniel Moore. With a valid ORS key and network, the app requests a driving route and displays distance, rounded-up minutes, route geometry, and endpoint markers on an OpenStreetMap map. Route information is shared above the results because all rides in a search have the same endpoints.

Book the ride, open My Rides, close/reopen the app to check local persistence, then cancel. Return to the home screen and the remaining seats refresh. Rebooking after cancellation is allowed. Duplicate active bookings, exhausted seats, missing rides, and the supplied four-passenger-per-driver/time limit are checked in a Room transaction. Cancelled records remain in Room as history; My Rides shows active bookings only.

**Head Hall → Downtown** demonstrates the original 16:00 ride and local booking. Routing for Downtown is blocked until a precise, verified destination point is supplied.

Times are a repeating demo schedule, without service dates, live drivers, or real dispatch. No location/GPS permissions are requested. No accounts, payments, tracking, or emergency features are implemented.

## Data and coordinates

`locations.json`, `drivers.json`, and `rides.json` seed Room once, together in a transaction. Validation checks IDs, relationships, required text, positive capacity, departure-time format, and coordinate ranges. Normal screens query Room; booking never rewrites JSON. The original assets contain six locations, four drivers, and eight rides.

Only two coordinates could be verified during implementation:

| Location | Latitude | Longitude | Source |
| --- | --- | --- | --- |
| Head Hall | 45.94964 | -66.64195 | [Mapcarta / OSM way 111404729](https://mapcarta.com/W111404729) |
| Regent Mall | 45.93531 | -66.66455 | [Mapcarta / OSM way 35061852](https://mapcarta.com/W35061852) |

These are mapped building positions, not verified vehicle pickup entrances. ORS may snap them to nearby roads; inspect the resulting route before the demonstration. College Hill Road, J. Currie Center, Student Union, and Downtown have explicit `null` coordinate fields in the runtime asset. Supply verified coordinates/meeting points before routing those locations. No example or random coordinates are used. Original `Data/locations.json` remains unchanged.

Asset changes apply on first database initialization. For this disposable prototype, clear the application's storage/reinstall after editing seed data; this deletes demo bookings. There is no automatic destructive migration or reset button.

## Responsibilities

```text
assets/*.json → validated transactional seed → Room
Room DAO → pickup/destination lists, ride/driver joins, booking/cancellation
MainViewModel → search state and booking → XML / RecyclerView
Selected Room coordinates → Kotlin ORS HTTP client → typed GeoJSON response
Route distance/time → UI; route geometry → osmdroid map
My Rides → active Room bookings for demo student 1
```

- `data/Entities.kt`, `CarpoolDao.kt`, `AppDatabase.kt`, `JsonSeeder.kt`: local model, persistence and seed loading. Adapted from supplied Room files; originals preserved.
- `routing/`: typed route models, validation, and HTTPS POST using Android's HTTP client; no separate backend.
- `ui/MainViewModel.kt`: rotation-surviving home state and lifecycle-scoped operations.
- `MainActivity.kt`, `ui/MyRidesActivity.kt`, `ui/RideAdapter.kt`: presentation/navigation.
- XML under `res/`: screens, reusable ride card, text/theme resources.
- Manifest: launcher, My Rides, internet/network-state permissions.
- Gradle: Android/Kotlin/Room processing, dependencies, local key → BuildConfig.

The ORS request follows the [official GeoJSON directions contract](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/requests-and-return-types): coordinate pairs are longitude first; distance is meters and duration seconds. The key is not committed in Kotlin, but BuildConfig embeds it into the installed APK, appropriate only for this development prototype. Do not distribute an APK with a valuable unrestricted key.

Map tiles require network and use app-private cache storage. Attribution is visible. osmdroid 6.1.20 is a small, established prototype choice, but its upstream project is archived; revisit before production use. No separate map key is needed.

## Verification

`RouteModelsTest` covers typed GeoJSON parsing, coordinate order, empty routes, and malformed geometry. `DatabaseTest` contains Android Room tests for original assets, ID joins, duplicate booking, full rides, concurrent booking limits, driver limits, ownership-checked cancellation, reseeding, persistence after reopen, invalid relationships, and missing routing configuration. Their presence does not mean they have been executed; see `VERIFICATION.md` for actual results.

The standard Gradle 8.14.3 wrapper (`gradlew`, `gradlew.bat`, JAR and properties) was generated successfully with Gradle's `wrapper` task. The initial bootstrap used locally bundled Gradle classes; the final files are the canonical generated wrapper artifacts. Gradle wrapper code is Apache-2.0 licensed: https://github.com/gradle/gradle/blob/v8.14.3/LICENSE.
