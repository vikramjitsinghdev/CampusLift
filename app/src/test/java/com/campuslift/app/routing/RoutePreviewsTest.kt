package com.campuslift.app.routing

import org.junit.Assert.*
import org.junit.Test

class RoutePreviewsTest {
    @Test fun allSuppliedPairsHaveDistinctPreviews() {
        val images = listOf(1, 3, 4).flatMap { pickup ->
            listOf(2, 5, 6).map { destination -> RoutePreviews.image(pickup, destination) }
        }
        assertTrue(images.all { it != null })
        assertEquals(9, images.toSet().size)
    }

    @Test fun unsupportedAndMissingLocationsHaveNoPreview() {
        assertNull(RoutePreviews.image(2, 6))
        assertNull(RoutePreviews.image(null, 6))
        assertNull(RoutePreviews.image(4, null))
        assertNull(RoutePreviews.image(4, 1))
    }
}
