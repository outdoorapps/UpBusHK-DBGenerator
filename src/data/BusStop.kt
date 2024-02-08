package data

import com.beust.klaxon.Klaxon
import utils.Company

data class BusStop(
    val company: Company,
    val stopId: String,
    val engName: String,
    val chiTName: String,
    val chiSName: String,
    val latLngCoord: List<Double>
) {
    fun toJson() = Klaxon().toJsonString(this)
}