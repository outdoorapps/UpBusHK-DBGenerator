package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class GovernmentBusRouteData(
    @Json(name = "government_bus_routes", index = 1)
    val governmentBusRoutes: MutableList<GovernmentBusRoute> = mutableListOf(),
    @Json(name = "government_bus_stop_coordinates", index = 2)
    val governmentBusStopCoordinates: MutableMap<Int, List<Double>> = mutableMapOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}