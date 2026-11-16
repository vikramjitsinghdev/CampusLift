package com.campuslift.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

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

    @Query("SELECT * FROM rides")
    abstract suspend fun getAllRides(): List<Ride>

    @Query("DELETE FROM bookings WHERE rideId = :rideId")
    abstract suspend fun deleteBookingsForRide(rideId: Int)

    @Query("DELETE FROM rides WHERE rideId NOT IN (:keep)")
    abstract suspend fun deleteRidesNotIn(keep: List<Int>)

    @Query("DELETE FROM drivers WHERE driverId NOT IN (:keep)")
    abstract suspend fun deleteDriversNotIn(keep: List<Int>)

    @Query("DELETE FROM locations WHERE locationId NOT IN (:keep)")
    abstract suspend fun deleteLocationsNotIn(keep: List<Int>)

    /**
     * Makes the tables match the JSON seed files. Bookings are kept, except bookings
     * for rides that were removed or changed, because those trips no longer exist.
     */
    @Transaction
    open suspend fun applySeed(seed: SeedData) {
        val newRides = seed.rides.associateBy { it.rideId }
        getAllRides().filter { newRides[it.rideId] != it }.forEach { deleteBookingsForRide(it.rideId) }
        deleteRidesNotIn(seed.rides.map { it.rideId })
        upsertLocations(seed.locations)
        upsertDrivers(seed.drivers)
        upsertRides(seed.rides)
        deleteDriversNotIn(seed.drivers.map { it.driverId })
        deleteLocationsNotIn(seed.locations.map { it.locationId })
        saveSeedInfo(SeedInfo(hash = seed.hash))
    }

    // ---------- Dropdowns ----------
    @Query("SELECT * FROM locations WHERE locationId IN (SELECT pickupId FROM rides) ORDER BY name")
    abstract suspend fun getPickups(): List<Location>

    @Query("SELECT * FROM locations WHERE locationId IN (SELECT destinationId FROM rides) ORDER BY name")
    abstract suspend fun getDestinations(): List<Location>

    @Query("SELECT * FROM locations WHERE locationId = :locationId")
    abstract suspend fun getLocation(locationId: Int): Location?

    // ---------- Available times ----------
    // Every ride for this trip that can still be booked on the chosen date,
    // with the seats left on that date. Rides that already left today are hidden.
    @Query("""
        SELECT r.rideId, d.name AS driverName, d.carModel,
               p.name AS pickup, dest.name AS destination, r.departureTime,
               :serviceDate AS serviceDate,
               r.totalSeats - (SELECT COUNT(*) FROM bookings b
                               WHERE b.rideId = r.rideId AND b.status = 'confirmed'
                                 AND b.serviceDate = :serviceDate) AS seatsLeft
        FROM rides r
        JOIN drivers d      ON d.driverId = r.driverId
        JOIN locations p    ON p.locationId = r.pickupId
        JOIN locations dest ON dest.locationId = r.destinationId
        WHERE r.pickupId = :pickupId AND r.destinationId = :destId
          AND (:serviceDate > :today OR (:serviceDate = :today AND r.departureTime > :nowTime))
        ORDER BY r.departureTime
    """)
    protected abstract suspend fun availableRides(
        pickupId: Int, destId: Int, serviceDate: String, today: String, nowTime: String
    ): List<RideListItem>

    suspend fun availableRides(pickupId: Int, destId: Int, serviceDate: String, time: ScheduleTime): List<RideListItem> =
        availableRides(pickupId, destId, serviceDate, time.today, time.nowTime)

    // ---------- Booking helpers ----------
    @Query("SELECT * FROM rides WHERE rideId = :rideId")
    abstract suspend fun getRide(rideId: Int): Ride?

    @Query("""
        SELECT totalSeats - (SELECT COUNT(*) FROM bookings
                             WHERE rideId = :rideId AND serviceDate = :serviceDate AND status = 'confirmed')
        FROM rides WHERE rideId = :rideId
    """)
    abstract suspend fun seatsLeft(rideId: Int, serviceDate: String): Int

    // Total confirmed students a driver has at one departure (across all their rides that day)
    @Query("""
        SELECT COUNT(*) FROM bookings b
        JOIN rides r ON r.rideId = b.rideId
        WHERE r.driverId = :driverId AND r.departureTime = :time
          AND b.serviceDate = :serviceDate AND b.status = 'confirmed'
    """)
    abstract suspend fun driverLoadAt(driverId: Int, time: String, serviceDate: String): Int

    // The student's confirmed booking for one ride on one date, if there is one.
    @Query("""
        SELECT bookingId FROM bookings
        WHERE rideId = :rideId AND studentId = :studentId
          AND serviceDate = :serviceDate AND status = 'confirmed'
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
        if (time.hasDeparted(serviceDate, ride.departureTime)) return BookResult.DEPARTED
        if (confirmedBookingId(rideId, studentId, serviceDate) != null) return BookResult.ALREADY_BOOKED
        if (seatsLeft(rideId, serviceDate) <= 0) return BookResult.RIDE_FULL
        if (driverLoadAt(ride.driverId, ride.departureTime, serviceDate) >= MAX_PASSENGERS_PER_DRIVER) {
            return BookResult.DRIVER_FULL
        }
        insertBooking(Booking(rideId = rideId, studentId = studentId, serviceDate = serviceDate))
        return BookResult.SUCCESS
    }

    // ---------- Cancel ----------
    @Query("UPDATE bookings SET status = 'cancelled' WHERE bookingId = :bookingId AND studentId = :studentId AND status = 'confirmed' AND demoStage < 2")
    abstract suspend fun cancelBooking(bookingId: Int, studentId: Int): Int

    @Query("""
        UPDATE bookings SET demoStage = demoStage + 1,
            demoStartedAt = CASE WHEN demoStage = 1 THEN :now ELSE demoStartedAt END
        WHERE bookingId = :bookingId AND studentId = :studentId
          AND status = 'confirmed' AND demoStage = :expectedStage AND :expectedStage BETWEEN 0 AND 2
    """)
    abstract suspend fun advanceDemo(bookingId: Int, studentId: Int, expectedStage: Int, now: Long): Int

    // ---------- My Rides and Ride Details ----------
    // Every booking the student ever made, newest first. Nothing is deleted on cancel.
    @Query("""
        SELECT b.bookingId, r.pickupId, r.destinationId, d.name AS driverName, d.carModel, d.phone, d.plate,
               p.name AS pickup, dest.name AS destination, r.departureTime, b.serviceDate, b.status,
               b.demoStage, b.demoStartedAt
        FROM bookings b
        JOIN rides r        ON r.rideId = b.rideId
        JOIN drivers d      ON d.driverId = r.driverId
        JOIN locations p    ON p.locationId = r.pickupId
        JOIN locations dest ON dest.locationId = r.destinationId
        WHERE b.studentId = :studentId
        ORDER BY b.serviceDate DESC, r.departureTime DESC, b.bookingId DESC
    """)
    abstract suspend fun allBookings(studentId: Int): List<MyBookingItem>

    /** Active, completed or cancelled bookings. Active rides are listed soonest first. */
    suspend fun bookingsIn(state: RideState, studentId: Int, time: ScheduleTime): List<MyBookingItem> {
        val matching = allBookings(studentId).filter { time.stateOf(it) == state }
        return if (state == RideState.ACTIVE) matching.reversed() else matching
    }

    suspend fun activeBookings(studentId: Int, time: ScheduleTime): List<MyBookingItem> =
        bookingsIn(RideState.ACTIVE, studentId, time)

    suspend fun getBooking(bookingId: Int, studentId: Int): MyBookingItem? =
        allBookings(studentId).firstOrNull { it.bookingId == bookingId }

    // ---------- Demo reset ----------
    @Query("DELETE FROM bookings")
    abstract suspend fun clearAllBookings()
}
