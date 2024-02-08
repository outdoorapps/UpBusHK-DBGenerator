package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class RSDatabase(
    @Json(name = "bus_routes") val busRoutes: List<BusRoute>, @Json(name = "bus_stops") val busStops: List<BusStop>
) {
    fun toJson() = Klaxon().toJsonString(this)
}