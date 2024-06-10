package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import util.Bound
import util.Company

data class BusRoute(
    @Json(index = 1) val companies: Set<Company>,
    @Json(index = 2) val number: String,
    @Json(index = 3) val bound: Bound,
    @Json(index = 4) val secondaryBound: Bound?,
    @Json(index = 5) val originEn: String,
    @Json(index = 6) val originChiT: String,
    @Json(index = 7) val originChiS: String,
    @Json(index = 8) val destEn: String,
    @Json(index = 9) val destChiT: String,
    @Json(index = 10) val destChiS: String,
    @Json(index = 11) val kmbServiceType: Int?,
    @Json(index = 12) val nlbRouteId: String?,
    @Json(index = 13) val trackId: Int?,
    @Json(index = 14) val fullFare: Double?,
    @Json(ignored = true) val stopFarePairs: List<Pair<String, Double?>>,
    @Json(index = 15) val stops: List<String>,
    @Json(index = 16) val secondaryStops: List<String>,
    @Json(index = 17) val fares: List<Double?>,
    @Json(ignored = true) val govRouteId: Int?,
    @Json(ignored = true) val govRouteSeq: Int?,
) {
    fun toJson() = Klaxon().toJsonString(this)
}