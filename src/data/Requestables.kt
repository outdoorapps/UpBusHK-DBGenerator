package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class Requestables(
    val requestableRoutes: MutableList<RequestableRoute> = mutableListOf(),
    @Json(name = "stops") val requestableStops: MutableList<RequestableStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}

data class RoutesStopsDatabase(
    @Json(name = "routes") val routes: List<Route>,
    @Json(name = "stops") val requestableStops: List<RequestableStop>
) {
    fun toJson() = Klaxon().toJsonString(this)
}
