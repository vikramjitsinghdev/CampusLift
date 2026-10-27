package com.campuslift.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class CarpoolDao {

    // ---------- Seeding ----------
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertLocations(items: List<Location>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDrivers(items: List<Driver>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRides(items: List<Ride>)

    @Query("SELECT COUNT(*) FROM locations")
    abstract suspend fun locationCount(): Int

    // ---------- Dropdowns ----------
    @Query("SELECT * FROM locations WHERE locationId IN (SELECT pickupId FROM rides) ORDER BY name")
    abstract suspend fun getPickups(): List<Location>

    @Query("SELECT * FROM locations WHERE locationId IN (SELECT destinationId FROM rides) ORDER BY name")
    abstract suspend fun getDestinations(): List<Location>

    // ---------- Browse ----------
    @Query("""
        SELECT r.rideId, d.name AS driverName, d.carModel,
               p.name AS pickup, dest.name AS destination, r.departureTime,
               r.totalSeats - (SELECT COUNT(*) FROM bookings b
                               WHERE b.rideId = r.rideId AND b.status = 'confirmed') AS seatsLeft
        FROM rides r
        JOIN drivers d      ON d.driverId = r.driverId
        JOIN locations p    ON p.locationId = r.pickupId
        JOIN locations dest ON dest.locationId = r.destinationId
        WHERE r.pickupId = :pickupId AND r.destinationId = :destId
        ORDER BY r.departureTime
    """)
    abstract suspend fun searchRides(pickupId: Int, destId: Int): List<RideListItem>

    // ---------- Booking helpers ----------
    @Query("SELECT * FROM rides WHERE rideId = :rideId")
    abstract suspend fun getRide(rideId: Int): Ride?

    @Query("""
        SELECT totalSeats - (SELECT COUNT(*) FROM bookings
                             WHERE rideId = :rideId AND status = 'confirmed')
        FROM rides WHERE rideId = :rideId
    """)
    abstract suspend fun seatsLeft(rideId: Int): Int

    // Total confirmed students a driver has at one departure time (across all their rides)
    @Query("""
        SELECT COUNT(*) FROM bookings b
        JOIN rides r ON r.rideId = b.rideId
        WHERE r.driverId = :driverId AND r.departureTime = :time AND b.status = 'confirmed'
    """)
    abstract suspend fun driverLoadAt(driverId: Int, time: String): Int

    @Query("SELECT COUNT(*) FROM bookings WHERE rideId = :rideId AND studentId = :studentId AND status = 'confirmed'")
    abstract suspend fun activeBookingCount(rideId: Int, studentId: Int): Int

    @Insert
    abstract suspend fun insertBooking(booking: Booking)

    // ---------- Book (runs as one transaction) ----------
    // "open" is required so Room can wrap this function in a transaction
    @Transaction
    open suspend fun bookRide(rideId: Int, studentId: Int = 1): BookResult {
        val ride = getRide(rideId) ?: return BookResult.NOT_FOUND
        if (activeBookingCount(rideId, studentId) > 0) return BookResult.ALREADY_BOOKED
        if (seatsLeft(rideId) <= 0) return BookResult.RIDE_FULL
        if (driverLoadAt(ride.driverId, ride.departureTime) >= 4) return BookResult.DRIVER_FULL
        insertBooking(Booking(rideId = rideId, studentId = studentId))
        return BookResult.SUCCESS
    }

    // ---------- Cancel ----------
    @Query("UPDATE bookings SET status = 'cancelled' WHERE bookingId = :bookingId AND studentId = :studentId AND status = 'confirmed'")
    abstract suspend fun cancelBooking(bookingId: Int, studentId: Int = 1): Int

    // ---------- "My rides" screen ----------
    @Query("""
        SELECT b.bookingId, d.name AS driverName, d.carModel, d.phone,
               p.name AS pickup, dest.name AS destination, r.departureTime, b.status
        FROM bookings b
        JOIN rides r        ON r.rideId = b.rideId
        JOIN drivers d      ON d.driverId = r.driverId
        JOIN locations p    ON p.locationId = r.pickupId
        JOIN locations dest ON dest.locationId = r.destinationId
        WHERE b.studentId = :studentId AND b.status = 'confirmed'
        ORDER BY r.departureTime
    """)
    abstract suspend fun myBookings(studentId: Int = 1): List<MyBookingItem>

    // ---------- Demo reset ----------
    @Query("DELETE FROM bookings")
    abstract suspend fun clearAllBookings()
}
