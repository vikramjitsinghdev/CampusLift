package com.campuslift.app.routing

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class RouteModelsTest {
    @Test fun parsesGeoJsonInLongitudeLatitudeOrder() {
        val json = """{"features":[{"properties":{"summary":{"distance":4800,"duration":540}},"geometry":{"type":"LineString","coordinates":[[-66.64,45.95],[-66.66,45.93]]}}]}"""
        val route = requireNotNull(Gson().fromJson(json, DirectionsResponse::class.java).toRoute())
        assertEquals(4800.0, route.distanceMeters, 0.0)
        assertEquals(540.0, route.durationSeconds, 0.0)
        assertEquals(GeoPoint(45.95, -66.64), route.points.first())
    }

    @Test fun emptyFeaturesMeanNoRoute() {
        assertNull(DirectionsResponse(emptyList()).toRoute())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeGeometry() {
        DirectionsResponse(listOf(RouteFeature(RouteProperties(RouteSummary(1.0, 1.0)),
            RouteGeometry("LineString", listOf(listOf(-66.0, 95.0), listOf(-66.0, 45.0)))))).toRoute()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingSummary() {
        DirectionsResponse(listOf(RouteFeature(null, null))).toRoute()
    }
}
