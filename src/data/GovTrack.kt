package data

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import com.programmerare.crsTransformations.coordinate.CrsCoordinate

data class GovTrack(val trackInfo: TrackInfo, val multiLineString: List<CrsCoordinate>) {
    fun toJson() = Klaxon().toJsonString(this)
}

data class TrackInfo(
    @Json(index = 1, name = "OBJECTID") val objectId: Int,
    @Json(index = 2, name = "ROUTE_ID") val routeId: Int,
    @Json(index = 3, name = "ROUTE_SEQ") val routeSeq: Int,
    @Json(index = 4, name = "COMPANY_CODE") val companyCode: String,
    @Json(index = 5, name = "ROUTE_NAMEE") val routeNameE: String,
    @Json(index = 6, name = "ST_STOP_ID") val stStopId: Int,
    @Json(index = 7, name = "ST_STOP_NAMEE") val stStopNameE: String,
    @Json(index = 8, name = "ST_STOP_NAMEC") val stStopNameC: String,
    @Json(index = 9, name = "ST_STOP_NAMES") val stStopNameS: String,
    @Json(index = 10, name = "ED_STOP_ID") val edStopId: Int,
    @Json(index = 11, name = "ED_STOP_NAMEE") val edStopNameE: String,
    @Json(index = 12, name = "ED_STOP_NAMEC") val edStopNameC: String,
    @Json(index = 13, name = "ED_STOP_NAMES") val edStopNameS: String,
    @Json(index = 14, name = "Shape_Length") val shapeLength: Double
)
