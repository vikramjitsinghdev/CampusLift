package com.campuslift.app.data

import androidx.room.ColumnInfo

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** The app has no accounts yet, so every booking belongs to this one local demo student. */
object DemoStudent { const val ID = 1 }

/** A SafeRide vehicle carries at most this many students at one departure. */
const val MAX_PASSENGERS_PER_DRIVER = 4

@Entity(tableName = "locations")
data class Location(
    @PrimaryKey val locationId: Int,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Entity(tableName = "drivers")
data class Driver(
    @PrimaryKey val driverId: Int,
    val name: String,
    val carModel: String,
    val phone: String,
    val plate: String? = null
)

/** One entry in the daily schedule. It repeats every day at [departureTime]. */
@Entity(
    tableName = "rides",
    foreignKeys = [
        ForeignKey(entity = Driver::class,   parentColumns = ["driverId"],   childColumns = ["driverId"]),
        ForeignKey(entity = Location::class, parentColumns = ["locationId"], childColumns = ["pickupId"]),
        ForeignKey(entity = Location::class, parentColumns = ["locationId"], childColumns = ["destinationId"])
    ],
    indices = [Index("driverId"), Index("pickupId"), Index("destinationId")]
)
data class Ride(
    @PrimaryKey val rideId: Int,
    val driverId: Int,
    val pickupId: Int,
    val destinationId: Int,
    val departureTime: String,   // "17:00"
    val totalSeats: Int = 4
)

/** Stored in the database as the text "confirmed" or "cancelled". */
enum class BookingStatus { CONFIRMED, CANCELLED }

class Converters {
    @TypeConverter fun statusToText(status: BookingStatus): String = status.name.lowercase()
    @TypeConverter fun textToStatus(text: String): BookingStatus = BookingStatus.valueOf(text.uppercase())
}

/** A seat on one ride on one specific day ([serviceDate], for example "2026-11-16"). */
@Entity(
    tableName = "bookings",
    foreignKeys = [ForeignKey(entity = Ride::class, parentColumns = ["rideId"], childColumns = ["rideId"])],
    indices = [Index("rideId")]
)
data class Booking(
    @PrimaryKey(autoGenerate = true) val bookingId: Int = 0,
    val rideId: Int,
    val studentId: Int,
    val serviceDate: String,
    val status: BookingStatus = BookingStatus.CONFIRMED,
    @ColumnInfo(defaultValue = "0") val demoStage: Int = 0,
    val demoStartedAt: Long? = null
)

/** Remembers which version of the JSON seed files is loaded, so edits are picked up. */
@Entity(tableName = "seed_info")
data class SeedInfo(
    @PrimaryKey val id: Int = 1,
    val hash: String
)

// Result shapes for the screens (not tables)
data class RideListItem(
    val rideId: Int,
    val driverName: String,
    val carModel: String,
    val pickup: String,
    val destination: String,
    val departureTime: String,
    val serviceDate: String,
    val seatsLeft: Int
)

data class MyBookingItem(
    val bookingId: Int,
    val pickupId: Int,
    val destinationId: Int,
    val driverName: String,
    val carModel: String,
    val phone: String,
    val pickup: String,
    val destination: String,
    val departureTime: String,
    val serviceDate: String,
    val status: BookingStatus,
    val plate: String?,
    val demoStage: Int,
    val demoStartedAt: Long?
)

// Result of trying to book
enum class BookResult { SUCCESS, RIDE_FULL, DRIVER_FULL, ALREADY_BOOKED, DEPARTED, NOT_FOUND }
