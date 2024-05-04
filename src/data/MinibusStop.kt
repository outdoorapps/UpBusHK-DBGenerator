package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class MinibusStop(
    @Json(index = 1) val stopId: Int,
    @Json(index = 2) val engName: String,
    @Json(index = 3) val chiTName: String,
    @Json(index = 4) val chiSName: String,
    @Json(index = 5) val coordinate: List<Double>
) {
    fun toJson() = Klaxon().toJsonString(this)
}