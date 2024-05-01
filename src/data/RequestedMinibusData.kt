package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class RequestedMinibusData(
    @Json(index = 1) val minibusRoutes: MutableList<MiniBusRoute> = mutableListOf(),
    @Json(name = "minibus_stops", index = 2) val minibusStops: MutableSet<MiniBusStop> = mutableSetOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}