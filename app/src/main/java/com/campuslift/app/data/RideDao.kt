package com.campuslift.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.LocalDate

@Dao
abstract class RideDao {

    // ---------- Seeding ----------
    @Upsert abstract suspend fun upsertLocations(items: List<Location>)
    @Upsert abstract suspend fun upsertDrivers(items: List<Driver>)
    @Upsert abstract suspend fun upsertRides(items: List<Ride>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveSeedInfo(info: SeedInfo)

    @Query("SELECT hash FROM seed_info WHERE id = 1")
    abstract suspend fun seedHash(): String?

    @Query("SELECT COUNT(*) FROM locations")
    abstract suspend fun locationCount(): Int

    @Query("SELECT * FROM rides WHERE retired = 0")
    abstract suspend fun getAllRides(): List<Ride>

    @Query("UPDATE rides SET retired = 1 WHERE rideId NOT IN (:keep)")
    abstract suspend fun retireRidesNotIn(keep: List<Int>)

    @Query("DELETE FROM rides WHERE rideId NOT IN (:keep) AND rideId NOT IN (SELECT rideId FROM bookings)")
    abstract suspend fun deleteUnbookedRidesNotIn(keep: List<Int>)

    @Query("DELETE FROM drivers WHERE driverId NOT IN (:keep) AND driverId NOT IN (SELECT driverId FROM rides)")
    abstract suspend fun deleteUnusedDriversNotIn(keep: List<Int>)

    @Query("DELETE FROM locations WHERE locationId NOT IN (:keep) AND locationId NOT IN (SELECT pickupId FROM rides) AND locationId NOT IN (SELECT destinationId FROM rides)")
    abstract suspend fun deleteUnusedLocationsNotIn(keep: List<Int>)

    /**
     * Makes active schedule tables match the JSON seed files without ever deleting bookings.
     * Rides removed from the seed that already have bookings are retired instead of deleted.
     */
    @Transaction
    open suspend fun applySeed(seed: SeedData) {
        val keepRides = seed.rides.map { it.rideId }
        upsertLocations(seed.locations)
        upsertDrivers(seed.drivers)
        upsertRides(seed.rides.map { it.copy(retired = false) })
        retireRidesNotIn(keepRides)
        deleteUnbookedRidesNotIn(keepRides)
        deleteUnusedDriversNotIn(seed.drivers.map { it.driverId })
        deleteUnusedLocationsNotIn(seed.locations.map { it.locationId })
        saveSeedInfo(SeedInfo(hash = seed.hash))
    }

    // ---------- Dropdowns ----------
    @Query("SELECT * FROM locations WHERE locationId IN (SELECT pickupId FROM rides WHERE retired = 0) ORDER BY name")
    abstract suspend fun getPickups(): List<Location>

    @Query("SELECT * FROM locations WHERE locationId IN (SELECT destinationId FROM rides WHERE retired = 0) ORDER BY name")
    abstract suspend fun getDestinations(): List<Location>

    @Query("SELECT * FROM locations WHERE locationId = :locationId")
    abstract suspend fun getLocation(locationId: Int): Location?

    @Query("SELECT * FROM drivers WHERE driverId = :driverId")
    abstract suspend fun getDriver(driverId: Int): Driver?

    // ---------- Available times ----------
    // Every active ride for this trip that can still be booked on the chosen date,
    // with the seats left on that date. Rides that already left today are hidden.
    @Query("""
        SELECT r.rideId, d.name AS driverName, d.carModel, d.plate,
               p.name AS pickup, dest.name AS destination, r.departureTime,
               :serviceDate AS serviceDate,
               MAX(0, r.totalSeats - (
                   SELECT COUNT(*) FROM bookings b
                   WHERE b.rideId = r.rideId AND b.status = 'confirmed'
                     AND b.serviceDate = :serviceDate
                     AND b.departureTime = r.departureTime
                     AND b.pickupId = r.pickupId
                     AND b.destinationId = r.destinationId
               )) AS seatsLeft
        FROM rides r
        JOIN drivers d      ON d.driverId = r.driverId
        JOIN locations p    ON p.locationId = r.pickupId
        JOIN locations dest ON dest.locationId = r.destinationId
        WHERE r.retired = 0
          AND r.pickupId = :pickupId AND r.destinationId = :destId
          AND (:serviceDate > :today OR (:serviceDate = :today AND r.departureTime > :nowTime))
        ORDER BY r.departureTime
    """)
    protected abstract suspend fun availableRides(
        pickupId: Int, destId: Int, serviceDate: String, today: String, nowTime: String
    ): List<RideListItem>

    suspend fun availableRides(pickupId: Int, destId: Int, serviceDate: String, time: ScheduleTime): List<RideListItem> =
        availableRides(pickupId, destId, serviceDate, time.today, time.nowTime)

    @Transaction
    open suspend fun shiftRides(pickupId: Int, destId: Int, date: String, time: ScheduleTime): List<RideListItem> {
        val nextDay = LocalDate.parse(date).plusDays(1).toString()
        return availableRides(pickupId, destId, date, time).filter { it.departureTime >= "15:00" } +
            availableRides(pickupId, destId, nextDay, time).filter { it.departureTime < "02:00" }
    }

    // ---------- Booking helpers ----------
    @Query("SELECT * FROM rides WHERE rideId = :rideId")
    abstract suspend fun getRide(rideId: Int): Ride?

    @Query("""
        SELECT MAX(0, r.totalSeats - (
            SELECT COUNT(*) FROM bookings b
            WHERE b.rideId = r.rideId AND b.serviceDate = :serviceDate AND b.status = 'confirmed'
              AND b.departureTime = r.departureTime
              AND b.pickupId = r.pickupId
              AND b.destinationId = r.destinationId
        ))
        FROM rides r WHERE r.rideId = :rideId
    """)
    abstract suspend fun seatsLeft(rideId: Int, serviceDate: String): Int

    // Total confirmed students a driver has at one departure (across all their rides that day)
    @Query("""
        SELECT COUNT(*) FROM bookings b
        JOIN rides r ON r.rideId = b.rideId
        WHERE r.driverId = :driverId AND b.departureTime = :time
          AND r.departureTime = b.departureTime
          AND b.serviceDate = :serviceDate AND b.status = 'confirmed'
    """)
    abstract suspend fun driverLoadAt(driverId: Int, time: String, serviceDate: String): Int

    // The student's confirmed booking for one ride on one date, if there is one.
    @Query("""
        SELECT b.bookingId FROM bookings b
        JOIN rides r ON r.rideId = b.rideId
        WHERE b.rideId = :rideId AND b.studentId = :studentId
          AND b.serviceDate = :serviceDate AND b.status = 'confirmed'
          AND b.departureTime = r.departureTime
          AND b.pickupId = r.pickupId
          AND b.destinationId = r.destinationId
        LIMIT 1
    """)
    abstract suspend fun confirmedBookingId(rideId: Int, studentId: Int, serviceDate: String): Int?

    @Insert
    abstract suspend fun insertBooking(booking: Booking)

    // ---------- Book (runs as one transaction) ----------
    // "open" is required so Room can wrap this function in a transaction.
    @Transaction
    open suspend fun bookRide(rideId: Int, studentId: Int, serviceDate: String, time: ScheduleTime): BookResult {
        val ride = getRide(rideId) ?: return BookResult.NOT_FOUND
        if (ride.retired) return BookResult.NOT_FOUND
        if (time.hasDeparted(serviceDate, ride.departureTime)) return BookResult.DEPARTED
        if (confirmedBookingId(rideId, studentId, serviceDate) != null) return BookResult.ALREADY_BOOKED
        if (seatsLeft(rideId, serviceDate) <= 0) return BookResult.RIDE_FULL
        if (driverLoadAt(ride.driverId, ride.departureTime, serviceDate) >= MAX_PASSENGERS_PER_DRIVER) {
            return BookResult.DRIVER_FULL
        }
        val driver = getDriver(ride.driverId) ?: return BookResult.NOT_FOUND
        val pickup = getLocation(ride.pickupId) ?: return BookResult.NOT_FOUND
        val destination = getLocation(ride.destinationId) ?: return BookResult.NOT_FOUND
        insertBooking(
            Booking(
                rideId = rideId,
                studentId = studentId,
                serviceDate = serviceDate,
                pickupId = ride.pickupId,
                destinationId = ride.destinationId,
                pickup = pickup.name,
                destination = destination.name,
                driverName = driver.name,
                carModel = driver.carModel,
                phone = driver.phone,
                plate = driver.plate,
                departureTime = ride.departureTime
            )
        )
        return BookResult.SUCCESS
    }

    // ---------- Cancel ----------
    @Query("""
        UPDATE bookings SET status = 'cancelled'
        WHERE bookingId = :bookingId AND studentId = :studentId
          AND status = 'confirmed' AND demoStage < 2
          AND (serviceDate > :activeDate OR (serviceDate = :activeDate AND departureTime >= :activeTime))
    """)
    protected abstract suspend fun cancelBookingIfActive(
        bookingId: Int, studentId: Int, activeDate: String, activeTime: String
    ): Int

    /** Cancels a booking only if the student owns it and it is still inside the active window. */
    @Transaction
    open suspend fun cancelBooking(bookingId: Int, studentId: Int, time: ScheduleTime): Int =
        cancelBookingIfActive(bookingId, studentId, time.activeDate, time.activeTime)

    @Query("""
        UPDATE bookings SET demoStage = demoStage + 1,
            demoStartedAt = CASE WHEN demoStage = 1 THEN :now ELSE demoStartedAt END
        WHERE bookingId = :bookingId AND studentId = :studentId
          AND status = 'confirmed' AND demoStage = :expectedStage AND :expectedStage BETWEEN 0 AND 2
    """)
    abstract suspend fun advanceDemo(bookingId: Int, studentId: Int, expectedStage: Int, now: Long): Int

    // ---------- My Rides and Ride Details ----------
    // Every booking the student ever made, newest first. Nothing is deleted on cancel or seed sync.
    @Query("""
        SELECT b.bookingId, b.pickupId, b.destinationId, b.driverName, b.carModel, b.phone, b.plate,
               b.pickup, b.destination, b.departureTime, b.serviceDate, b.status,
               b.demoStage, b.demoStartedAt,
               CASE WHEN r.rideId IS NULL OR r.retired != 0
                         OR r.departureTime != b.departureTime
                         OR r.pickupId != b.pickupId
                         OR r.destinationId != b.destinationId
                    THEN 1 ELSE 0 END AS scheduleUnavailable
        FROM bookings b
        LEFT JOIN rides r ON r.rideId = b.rideId
        WHERE b.studentId = :studentId
        ORDER BY b.serviceDate DESC, b.departureTime DESC, b.bookingId DESC
    """)
    abstract suspend fun allBookings(studentId: Int): List<MyBookingItem>

    /** Active, completed or cancelled bookings. Active rides are listed soonest first. */
    suspend fun bookingsIn(state: RideState, studentId: Int, time: ScheduleTime): List<MyBookingItem> {
        val matching = allBookings(studentId).filter { time.stateOf(it) == state }
        return if (state == RideState.ACTIVE) matching.reversed() else matching
    }

    suspend fun activeBookings(studentId: Int, time: ScheduleTime): List<MyBookingItem> =
        bookingsIn(RideState.ACTIVE, studentId, time)

    @Query("""
        SELECT b.bookingId, b.pickupId, b.destinationId, b.driverName, b.carModel, b.phone, b.plate,
               b.pickup, b.destination, b.departureTime, b.serviceDate, b.status,
               b.demoStage, b.demoStartedAt,
               CASE WHEN r.rideId IS NULL OR r.retired != 0
                         OR r.departureTime != b.departureTime
                         OR r.pickupId != b.pickupId
                         OR r.destinationId != b.destinationId
                    THEN 1 ELSE 0 END AS scheduleUnavailable
        FROM bookings b
        LEFT JOIN rides r ON r.rideId = b.rideId
        WHERE b.bookingId = :bookingId AND b.studentId = :studentId
        LIMIT 1
    """)
    abstract suspend fun getBooking(bookingId: Int, studentId: Int): MyBookingItem?

    // ---------- Demo reset ----------
    @Query("DELETE FROM bookings")
    abstract suspend fun clearAllBookings()
}
