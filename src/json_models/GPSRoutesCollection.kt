package json_models

// To parse the JSON, install Klaxon and do:
//
//   val gpsRoutesCollection = GPSRoutesCollection.fromJson(jsonString)

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class GPSRoutesCollection(
    val type: String, val name: String, val crs: CRS,

    @Json(name = "features") val gpsRoutes: List<GPSRoute>
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = klaxon.parse<GPSRoutesCollection>(json)
    }
}

data class CRS(
    val type: String, val properties: CRSProperties
)

data class CRSProperties(
    val name: String
)

data class GPSRoute(
    val type: String, val mappedRouteGeometry: MappedRouteGeometry, val properties: RouteInfo
)

data class MappedRouteGeometry(
    val type: String, val coordinates: List<List<List<Double>>>
)

data class RouteInfo(
    @Json(name = "OBJECTID") val objectId: Int,
    @Json(name = "ROUTE_ID") val routeId: Int,
    @Json(name = "ROUTE_SEQ") val routeSeq: Int,
    @Json(name = "COMPANY_CODE") val companyCode: String,
    @Json(name = "ROUTE_NAMEE") val routeNameE: String,
    @Json(name = "ST_STOP_ID") val stStopId: Int,
    @Json(name = "ST_STOP_NAMEE") val stStopNameE: String,
    @Json(name = "ST_STOP_NAMEC") val stStopNameC: String,
    @Json(name = "ST_STOP_NAMES") val stStopNameS: String,
    @Json(name = "ED_STOP_ID") val edStopId: Int,
    @Json(name = "ED_STOP_NAMEE") val edStopNameE: String,
    @Json(name = "ED_STOP_NAMEC") val edStopNameC: String,
    @Json(name = "ED_STOP_NAMES") val edStopNameS: String,
    @Json(name = "Shape_Length") val shapeLength: Double
)
