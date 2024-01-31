package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class Path(@Json(index = 1) val id: Int, @Json(index = 2) val coords: List<List<Double>>) {
    fun toJson() = Klaxon().toJsonString(this)
}