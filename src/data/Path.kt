package data

import com.beust.klaxon.Klaxon

data class Path(val id: Int, val coords: List<List<Double>>) {
    fun toJson() = Klaxon().toJsonString(this)
}