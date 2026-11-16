package com.campuslift.app.ui

internal object DemoRide {
    const val DURATION_MS = 30_000L
    private const val QR_DURATION_MS = 120_000L

    fun code(bookingId: Int, stage: Int, now: Long): String =
        "CampusLift-demo:$bookingId:$stage:${now + QR_DURATION_MS}"

    fun accepts(code: String, bookingId: Int, stage: Int, now: Long): Boolean {
        val parts = code.split(':', limit = 5)
        if (stage !in 0..1 || parts.size != 4) return false
        val expires = parts[3].toLongOrNull() ?: return false
        return parts[0] == "CampusLift-demo" && parts[1] == bookingId.toString() &&
            parts[2] == stage.toString() && expires in now..(now + QR_DURATION_MS)
    }

    fun progress(startedAt: Long, now: Long): Double =
        ((now - startedAt).toDouble() / DURATION_MS).coerceIn(0.0, 1.0)
}
