// To parse the JSON, install Klaxon and do:
//
//   val routeInfo = RouteInfo.fromJson(jsonString)

package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class BusRouteStopCollection(
    val type: String, @Json(name = "features") val govRouteStops: List<GovRouteStop>
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<BusRouteStopCollection>(json)
    }
}

data class GovRouteStop(
    val type: String, val geometry: LongLatGeometry, @Json(name = "properties") val info: GovRouteStopInfo
)

data class GovRouteStopInfo(
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
