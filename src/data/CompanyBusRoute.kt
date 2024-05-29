package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import util.Bound
import util.Company

data class CompanyBusRoute(
    @Json(index = 1) val company: Company,
    @Json(index = 2) val number: String,
    @Json(index = 3) val bound: Bound,
    @Json(index = 4) val originEn: String,
    @Json(index = 5) val originChiT: String,
    @Json(index = 6) val originChiS: String,
    @Json(index = 7) val destEn: String,
    @Json(index = 8) val destChiT: String,
    @Json(index = 9) val destChiS: String,
    @Json(name = "service_type", index = 10) val kmbServiceType: Int?,
    @Json(index = 11) val nlbRouteId: String?,
    @Json(index = 12) val stops: List<String>
) {
    fun toJson() = Klaxon().toJsonString(this)
}