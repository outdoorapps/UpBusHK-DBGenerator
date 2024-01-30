package data

import utils.Bound
import utils.Company
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class CompanyRoute(
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