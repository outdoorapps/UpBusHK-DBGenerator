package data

import com.beust.klaxon.Klaxon
import utils.Bound
import utils.Region

data class MiniBusRoute(
    val routeId: Int,
    val region: Region,
    val number: String,
    val bound: Bound,
    val originEn: String,
    val originChiT: String,
    val originChiS: String,
    val destEn: String,
    val destChiT: String,
    val destChiS: String,
    val stops: List<Int>
    //val fares: List<Double> decimal Int
) {
    fun toJson() = Klaxon().toJsonString(this)
}