package data

import com.beust.klaxon.Klaxon

data class MiniBusStop(
    val stopId: String,
    val engName: String,
    val chiTName: String,
    val chiSName: String,
    val latLngCoord: List<Double>
) {
    fun toJson() = Klaxon().toJsonString(this)
}