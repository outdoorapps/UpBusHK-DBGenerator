package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class BusTrack(@Json(index = 1) val trackId: Int, @Json(index = 2) val coordinate: List<List<Double>>) {
    fun toJson() = Klaxon().toJsonString(this)
}