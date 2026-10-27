package com.campuslift.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val phone: String
)

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

@Entity(
    tableName = "bookings",
    foreignKeys = [ForeignKey(entity = Ride::class, parentColumns = ["rideId"], childColumns = ["rideId"])],
    indices = [Index("rideId")]
)
data class Booking(
    @PrimaryKey(autoGenerate = true) val bookingId: Int = 0,
    val rideId: Int,
    val studentId: Int = 1,
    val status: String = "confirmed"   // confirmed, cancelled
)

// Result shapes for the screens (not tables)
data class RideListItem(
    val rideId: Int,
    val driverName: String,
    val carModel: String,
    val pickup: String,
    val destination: String,
    val departureTime: String,
    val seatsLeft: Int
)

data class MyBookingItem(
    val bookingId: Int,
    val driverName: String,
    val carModel: String,
    val phone: String,
    val pickup: String,
    val destination: String,
    val departureTime: String,
    val status: String
)

// Result of trying to book
enum class BookResult { SUCCESS, RIDE_FULL, DRIVER_FULL, ALREADY_BOOKED, NOT_FOUND }
