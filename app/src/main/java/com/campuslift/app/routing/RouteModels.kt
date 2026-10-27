package com.campuslift.app.routing

data class GeoPoint(val latitude: Double, val longitude: Double)
data class DrivingRoute(val distanceMeters: Double, val durationSeconds: Double, val points: List<GeoPoint>)
enum class RouteError { MISSING_KEY, MISSING_COORDINATES, NETWORK, SERVICE, NO_ROUTE, INVALID_RESPONSE }
sealed interface RouteResult {
    data class Success(val route: DrivingRoute) : RouteResult
    data class Failure(val error: RouteError) : RouteResult
}

// ORS GeoJSON uses longitude first. These classes mirror only the fields we need.
data class DirectionsResponse(val features: List<RouteFeature>?)
data class RouteFeature(val properties: RouteProperties?, val geometry: RouteGeometry?)
data class RouteProperties(val summary: RouteSummary?)
data class RouteSummary(val distance: Double?, val duration: Double?)
data class RouteGeometry(val type: String?, val coordinates: List<List<Double>>?)

fun DirectionsResponse.toRoute(): DrivingRoute? {
    val feature = features?.firstOrNull() ?: return null
    val summary = requireNotNull(feature.properties?.summary)
    val distance = requireNotNull(summary.distance)
    val duration = requireNotNull(summary.duration)
    require(distance.isFinite() && distance >= 0 && duration.isFinite() && duration >= 0)
    val geometry = requireNotNull(feature.geometry)
    require(geometry.type == "LineString")
    val points = requireNotNull(geometry.coordinates).map {
        require(it.size >= 2 && it[0].isFinite() && it[1].isFinite())
        require(it[0] in -180.0..180.0 && it[1] in -90.0..90.0)
        GeoPoint(it[1], it[0])
    }
    require(points.size >= 2)
    return DrivingRoute(distance, duration, points)
}
