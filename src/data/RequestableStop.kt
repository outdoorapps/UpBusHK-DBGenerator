package data

import Company
import com.beust.klaxon.Klaxon

data class RequestableStop(
    val company: Company,
    val stopId: String,
    val engName: String,
    val chiTName: String,
    val chiSName: String,
    val latLng: List<Double>
) {
    fun toJson() = Klaxon().toJsonString(this)
}