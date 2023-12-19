package data

import Bound
import Company
import com.beust.klaxon.Klaxon

data class Route(
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
    val serviceType: String?,
) {
    public fun toJson() = Klaxon().toJsonString(this)
}