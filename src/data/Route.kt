package data

import utils.Bound
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class Route(
    val companyCode: String,
    val number: String,
    val bound: Bound,
    val originEn: String,
    val originChiT: String,
    val originChiS: String,
    val destEn: String,
    val destChiT: String,
    val destChiS: String,
    @Json(name = "service_type") val kmbServiceType: Int?,
    val nlbRouteId: String?,
    val pathId: Int?,
    val stops: List<String>,
    val secondaryStops: List<String>
    //val fares: List<Double> decimal Int
) {
    fun toJson() = Klaxon().toJsonString(this)
}