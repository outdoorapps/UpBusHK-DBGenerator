package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class RequestedMinibusData(
    val minibusRoutes: MutableList<MiniBusRoute> = mutableListOf(),
    @Json(name = "minibus_stops")
    val minibusStops: MutableList<MiniBusStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}