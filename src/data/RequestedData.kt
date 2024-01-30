package data

import com.beust.klaxon.Klaxon


data class RequestedData(
    val companyRoutes: MutableList<CompanyRoute> = mutableListOf(), val stops: MutableList<Stop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
