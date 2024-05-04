// To parse the JSON, install Klaxon and do:
//
//   val routeInfo = RouteInfo.fromJson(jsonString)

package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class BusRouteStopCollection(
    val type: String, @Json(name = "features") val governmentRouteStops: List<GovernmentRouteStop>
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = klaxon.parse<BusRouteStopCollection>(json)
    }
}

data class GovernmentRouteStop(
    val type: String, val geometry: Geometry, @Json(name = "properties") val info: GovernmentRouteStopInfo
)

data class Geometry(val type: String, @Json(name = "coordinates") val longLatCoordinates: List<Double>)

data class GovernmentRouteStopInfo(
    @Json(name = "routeId") val routeID: Int,
    val companyCode: String,
    val district: String,
    val routeNameC: String,
    val routeNameS: String,
    val routeNameE: String,
    val routeType: Int,
    val serviceMode: String,
    val specialType: Int,
    val journeyTime: Int,
    val locStartNameC: String,
    val locStartNameS: String,
    val locStartNameE: String,
    val locEndNameC: String,
    val locEndNameS: String,
    val locEndNameE: String,
    val hyperlinkC: String,
    val hyperlinkS: String,
    val hyperlinkE: String,
    val fullFare: Double,
    val lastUpdateDate: String,
    val routeSeq: Int,
    val stopSeq: Int,
    @Json(name = "stopId") val stopID: Int,
    val stopPickDrop: Int,
    val stopNameC: String,
    val stopNameS: String,
    val stopNameE: String
)
