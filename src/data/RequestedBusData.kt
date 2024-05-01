package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class RequestedBusData(
    val remoteBusRoutes: MutableList<RemoteBusRoute> = mutableListOf(),
    @Json(name = "bus_stops")
    val busStops: MutableList<BusStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
