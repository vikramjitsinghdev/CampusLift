package com.campuslift.app.routing

import com.campuslift.app.R

/** IDs refer to the original seed locations; names may change without breaking previews. */
object RoutePreviews {
    fun image(pickupId: Int?, destinationId: Int?): Int? = when (pickupId to destinationId) {
        4 to 2 -> R.drawable.route_sub_headhall
        4 to 6 -> R.drawable.route_sub_regent
        4 to 5 -> R.drawable.route_sub_downtown
        3 to 2 -> R.drawable.route_currie_headhall
        3 to 6 -> R.drawable.route_currie_regent
        3 to 5 -> R.drawable.route_currie_downtown
        1 to 2 -> R.drawable.route_college_headhall
        1 to 6 -> R.drawable.route_college_regent
        1 to 5 -> R.drawable.route_college_downtown
        else -> null
    }
}
