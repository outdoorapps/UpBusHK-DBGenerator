package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class MinibusData(
    @Json(index = 1, name = "minibus_routes") val minibusRoutes: List<MiniBusRoute>,
    @Json(index = 2, name = "minibus_stops") val minibusStops: List<MinibusStop>
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<MinibusData>(json)
    }

    fun toJson() = Klaxon().toJsonString(this)
}