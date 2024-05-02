package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class RemoteBusData(
    @Json(index = 1) val remoteBusRoutes: MutableList<RemoteBusRoute> = mutableListOf(),
    @Json(name = "bus_stops", index = 2) val busStops: MutableList<BusStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
