package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import util.Bound
import util.Region

data class MiniBusRoute(
    @Json(index = 1) val routeId: Int,
    @Json(index = 2) val region: Region,
    @Json(index = 3) val number: String,
    @Json(index = 4) val bound: Bound,
    @Json(index = 5) val originEn: String,
    @Json(index = 6) val originChiT: String,
    @Json(index = 7) val originChiS: String,
    @Json(index = 8) val destEn: String,
    @Json(index = 9) val destChiT: String,
    @Json(index = 10) val destChiS: String,
    @Json(index = 11) val fullFare: Double,
    @Json(index = 12) val stops: List<Int>
) {
    fun toJson() = Klaxon().toJsonString(this)
}