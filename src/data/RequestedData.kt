package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class RequestedData(
    val companyRoutes: MutableList<CompanyRoute> = mutableListOf(),
    @Json(name = "bus_stops")
    val busStops: MutableList<BusStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
