package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import utils.Company

data class BusStop(
    @Json(index = 1) val company: Company,
    @Json(index = 2) val stopId: String,
    @Json(index = 3) val engName: String,
    @Json(index = 4) val chiTName: String,
    @Json(index = 5) val chiSName: String,
    @Json(index = 6) val latLngCoord: List<Double>
) {
    fun toJson() = Klaxon().toJsonString(this)
}