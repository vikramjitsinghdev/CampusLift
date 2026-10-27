package com.campuslift.app.data

import android.content.Context
import com.google.gson.Gson

data class SeedData(val locations: List<Location>, val drivers: List<Driver>, val rides: List<Ride>)

object JsonSeeder {
    fun read(context: Context): SeedData {
        fun <T> readFile(name: String, type: Class<Array<T>>): List<T> =
            context.assets.open(name).bufferedReader().use {
                Gson().fromJson(it, type).toList()
            }
        val seed = SeedData(
            readFile("locations.json", Array<Location>::class.java),
            readFile("drivers.json", Array<Driver>::class.java),
            readFile("rides.json", Array<Ride>::class.java)
        )
        validate(seed)
        return seed
    }

    fun validate(seed: SeedData) {
        require(seed.locations.isNotEmpty() && seed.drivers.isNotEmpty() && seed.rides.isNotEmpty())
        val locations = seed.locations.map { it.locationId }.toSet()
        val drivers = seed.drivers.map { it.driverId }.toSet()
        require(locations.size == seed.locations.size && drivers.size == seed.drivers.size)
        require(seed.rides.map { it.rideId }.toSet().size == seed.rides.size)
        seed.locations.forEach {
            require(it.locationId > 0 && !it.name.isNullOrBlank())
            require((it.latitude == null) == (it.longitude == null))
            it.latitude?.let { lat -> require(lat.isFinite() && lat in -90.0..90.0) }
            it.longitude?.let { lon -> require(lon.isFinite() && lon in -180.0..180.0) }
        }
        seed.drivers.forEach {
            require(it.driverId > 0 && !it.name.isNullOrBlank() && !it.carModel.isNullOrBlank() && !it.phone.isNullOrBlank())
        }
        seed.rides.forEach {
            require(it.rideId > 0 && it.driverId in drivers && it.pickupId in locations && it.destinationId in locations)
            require(it.pickupId != it.destinationId && it.totalSeats > 0)
            require(it.departureTime?.matches(Regex("([01][0-9]|2[0-3]):[0-5][0-9]")) == true)
        }
    }
}
