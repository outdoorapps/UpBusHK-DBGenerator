package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import util.Bound
import util.Company

data class BusRoute(
    @Json(index = 1) val companies: Set<Company>,
    @Json(index = 2) val number: String,
    @Json(index = 3) val bound: Bound,
    @Json(index = 4) val originEn: String,
    @Json(index = 5) val originChiT: String,
    @Json(index = 6) val originChiS: String,
    @Json(index = 7) val destEn: String,
    @Json(index = 8) val destChiT: String,
    @Json(index = 9) val destChiS: String,
    @Json(index = 10) val kmbServiceType: Int?,
    @Json(index = 11) val nlbRouteId: String?,
    @Json(index = 12) val trackId: Int?,
    @Json(index = 13) val stops: List<String>, // todo new data structure for stop based fare
    @Json(index = 14) val secondaryStops: List<String>,
    @Json(index = 15) val fullFare: Double?
    //val fares: List<Double> decimal Int
) {
    fun toJson() = Klaxon().toJsonString(this)
}