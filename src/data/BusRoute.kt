package data

import utils.Bound
import com.beust.klaxon.Klaxon
import utils.Company

data class BusRoute(
    val companies: Set<Company>,
    val number: String,
    val bound: Bound,
    val originEn: String,
    val originChiT: String,
    val originChiS: String,
    val destEn: String,
    val destChiT: String,
    val destChiS: String,
    val kmbServiceType: Int?,
    val nlbRouteId: String?,
    val trackId: Int?,
    val stops: List<String>,
    val secondaryStops: List<String>
    //val fares: List<Double> decimal Int
) {
    fun toJson() = Klaxon().toJsonString(this)
}