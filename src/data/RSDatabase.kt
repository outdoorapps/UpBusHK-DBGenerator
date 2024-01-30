package data

import com.beust.klaxon.Klaxon

data class RSDatabase(
    val routes: List<Route>, val stops: List<Stop>
) {
    fun toJson() = Klaxon().toJsonString(this)
}