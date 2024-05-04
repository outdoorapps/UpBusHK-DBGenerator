package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class CompanyBusData(
    @Json(name = "company_bus_routes", index = 1) val companyBusRoutes: MutableList<CompanyBusRoute> = mutableListOf(),
    @Json(name = "bus_stops", index = 2) val busStops: MutableList<BusStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
