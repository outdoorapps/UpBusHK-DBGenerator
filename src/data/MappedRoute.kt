package data

import com.beust.klaxon.Klaxon
import com.programmerare.crsTransformations.coordinate.CrsCoordinate
import json_models.RouteInfo

private val klaxon = Klaxon()

data class TestData(val routeInfos: MutableList<RouteInfo> = mutableListOf()) {
    fun toJson() = Klaxon().toJsonString(this)
}

data class MappedRoute(val routeInfo: RouteInfo, val path: List<CrsCoordinate>) {
    fun toJson() = klaxon.toJsonString(this)
}

data class LatLng(val lat: Double, val long: Double)