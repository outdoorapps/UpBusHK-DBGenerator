package data

import Company
import com.beust.klaxon.Klaxon

data class RequestableStop(
    val company: Company,
    val stopId: String,
    val engName: String,
    val chiTName: String,
    val chiSName: String,
    val latLng: LatLng
) {
    public fun toJson() = Klaxon().toJsonString(this)
}