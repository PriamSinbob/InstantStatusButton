package com.instantstatusbutton.app.share

import android.location.Location

/** Where the device was when the button was tapped. */
data class Place(val latitude: Double, val longitude: Double, val name: String?) {
    val mapsUrl: String
        get() = "https://www.google.com/maps/search/?api=1&query=" +
            String.format(java.util.Locale.US, "%.6f,%.6f", latitude, longitude)

    companion object {
        fun of(l: Location, name: String?) = Place(l.latitude, l.longitude, name)
    }
}

object StatusComposer {
    private const val X_LIMIT = 280
    private const val X_LINK_LENGTH = 23 // X counts every URL as 23 characters

    /** Full text with place name and Maps link, used for LinkedIn and the clipboard. */
    fun full(status: String, place: Place?): String = buildString {
        append(status.trim())
        place?.let {
            append('\n')
            if (!it.name.isNullOrBlank()) append("📍 ").append(it.name).append('\n')
            else append("📍 ")
            append(it.mapsUrl)
        }
    }.trim()

    /** Same as [full], but the status is shortened so the post fits X's 280 characters. */
    fun forX(status: String, place: Place?): String {
        val suffix = place?.let {
            "\n📍 " + (if (!it.name.isNullOrBlank()) it.name + "\n" else "")
        }.orEmpty()
        val linkLen = if (place != null) X_LINK_LENGTH else 0
        // Emoji weigh 2 on X; the pin is the only one we add.
        val budget = X_LIMIT - linkLen - suffix.length - (if (place != null) 1 else 0)
        var s = status.trim()
        if (s.length > budget) s = s.take((budget - 1).coerceAtLeast(0)).trimEnd() + "…"
        return s + suffix + (place?.mapsUrl ?: "")
    }
}
