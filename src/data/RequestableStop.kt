package data

import Company
import com.beust.klaxon.Klaxon

data class RequestableStop(
    val company: Company,
    val stopId: String,
    val engName: String,
    val chiTName: String,
    val chiSName: String,
    val lat: Double,
    val long: Double
) {
    public fun toJson() = Klaxon().toJsonString(this)
}