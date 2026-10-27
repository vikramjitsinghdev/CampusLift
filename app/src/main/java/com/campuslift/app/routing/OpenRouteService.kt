package com.campuslift.app.routing

import com.campuslift.app.BuildConfig
import com.campuslift.app.data.Location
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class OpenRouteService(private val apiKey: String = BuildConfig.ORS_API_KEY) {
    suspend fun route(pickup: Location, destination: Location): RouteResult = withContext(Dispatchers.IO) {
        fun valid(location: Location): Boolean = location.latitude?.let { lat ->
            location.longitude?.let { lon -> lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 }
        } == true
        if (!valid(pickup) || !valid(destination)) return@withContext RouteResult.Failure(RouteError.MISSING_COORDINATES)
        if (apiKey.isBlank()) return@withContext RouteResult.Failure(RouteError.MISSING_KEY)
        val connection = URL("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/geo+json")
            connection.doOutput = true
            val body = Gson().toJson(mapOf("coordinates" to listOf(
                listOf(pickup.longitude, pickup.latitude), listOf(destination.longitude, destination.latitude)
            )))
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            if (connection.responseCode !in 200..299) return@withContext RouteResult.Failure(RouteError.SERVICE)
            val route = connection.inputStream.bufferedReader().use {
                Gson().fromJson(it, DirectionsResponse::class.java).toRoute()
            }
            route?.let { RouteResult.Success(it) } ?: RouteResult.Failure(RouteError.NO_ROUTE)
        } catch (_: IOException) {
            RouteResult.Failure(RouteError.NETWORK)
        } catch (_: JsonParseException) {
            RouteResult.Failure(RouteError.INVALID_RESPONSE)
        } catch (_: IllegalArgumentException) {
            RouteResult.Failure(RouteError.INVALID_RESPONSE)
        } catch (_: NullPointerException) {
            RouteResult.Failure(RouteError.INVALID_RESPONSE)
        } finally {
            connection.disconnect()
        }
    }
}
