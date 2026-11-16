package com.campuslift.app.data

import android.content.Context
import com.google.gson.Gson
import java.security.MessageDigest

/** [hash] changes whenever any seed file changes; the database uses it to notice edits. */
data class SeedData(
    val locations: List<Location>,
    val drivers: List<Driver>,
    val rides: List<Ride>,
    val hash: String = ""
)

object JsonSeeder {
    private val files = listOf("locations.json", "drivers.json", "rides.json")

    fun read(context: Context): SeedData {
        val text = files.associateWith { name ->
            context.assets.open(name).bufferedReader().use { it.readText() }
        }
        fun <T> parse(name: String, type: Class<Array<T>>): List<T> =
            Gson().fromJson(text.getValue(name), type).toList()
        val seed = SeedData(
            parse("locations.json", Array<Location>::class.java),
            parse("drivers.json", Array<Driver>::class.java),
            parse("rides.json", Array<Ride>::class.java),
            hash(files.map { text.getValue(it) })
        )
        validate(seed)
        return seed
    }

    private fun hash(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { digest.update(it.toByteArray(Charsets.UTF_8)) }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
