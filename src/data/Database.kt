package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class Database(
    val version: String,
    @Json(index = 1, name = "bus_routes") val busRoutes: List<BusRoute>,
    @Json(index = 2, name = "bus_stops") val busStops: List<BusStop>,
    @Json(index = 3, name = "minibus_routes") val minibusRoutes: List<MiniBusRoute>,
    @Json(index = 4, name = "minibus_stops") val minibusStops: Set<MinibusStop>
) {
    fun toJson() = Klaxon().toJsonString(this)
}