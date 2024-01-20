package data

import Bound
import Company
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class Route(
    val companyCode: String,
    val number: String,
    val bound: Bound,
    val originStopId: Int,
    val originEn: String,
    val originChiT: String,
    val originChiS: String,
    val destStopId: Int,
    val destEn: String,
    val destChiT: String,
    val destChiS: String,
    @Json(name = "service_type")
    val kmbServiceType: Int?,
    val nlbRouteId: String?,
    val routeId: Int?,
    val objectId: Int?,
    //val polyline: List<List<Double>>
    //val fares: List<Double> decimal Int
) {
    fun toJson() = Klaxon().toJsonString(this)
}