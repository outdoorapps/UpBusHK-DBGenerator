package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class GovBusData(
    @Json(name = "gov_bus_routes", index = 1) val govBusRoutes: MutableList<GovBusRoute> = mutableListOf(),
    @Json(index = 2) val govStops: MutableList<GovStop> = mutableListOf()
) {
    companion object {
        fun fromJson(json: String) =
            Klaxon().fieldConverter(StopFarePairs::class, pairsConverter).parse<GovBusData>(json)
    }

    fun toJson() = Klaxon().toJsonString(this)
}