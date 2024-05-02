package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class MinibusData(
    @Json(index = 1, name = "minibus_routes") val minibusRoutes: MutableList<MiniBusRoute> = mutableListOf(),
    @Json(index = 2, name = "minibus_stops") val minibusStops: MutableSet<MinibusStop> = mutableSetOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}