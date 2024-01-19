package data

import Bound
import Company
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class RequestableRoute(
    val company: Company,
    val number: String,
    val bound: Bound,
    val routeId: String?,
    val originEn: String,
    val originChiT: String,
    val originChiS: String,
    val destEn: String,
    val destChiT: String,
    val destChiS: String,
    @Json(name = "service_type")
    val kmbServiceType: Int?,
    val stops: List<String>
) {
    fun toJson() = Klaxon().toJsonString(this)
}