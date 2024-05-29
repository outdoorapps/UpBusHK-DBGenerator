package json_model

// To parse the JSON, install Klaxon and do:
//
//   val minibusRouteStopCollection = MinibusRouteStopCollection.fromJson(jsonString)

import com.beust.klaxon.*


data class MinibusRouteStopCollection (
    val type: String,
    val features: List<MinibusRouteStopFeature>
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<MinibusRouteStopCollection>(json)
    }
}

data class MinibusRouteStopFeature (
    val type: String,
    val geometry: LongLatGeometry,
    @Json(name = "properties") val minibusRouteStopProperties: MinibusRouteStopProperties
)

data class MinibusRouteStopProperties (
    @Json(name = "routeId")
    val routeID: Long,

    val companyCode: String,
    val district: String,
    val routeNameC: String,
    val routeNameS: String,
    val routeNameE: String,
    val routeType: Long,
    val serviceMode: String,
    val specialType: Long,
    val journeyTime: Long,
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
    val routeSeq: Long,
    val stopSeq: Long,

    @Json(name = "stopId")
    val stopID: Long,

    val stopPickDrop: Long,
    val stopNameC: String,
    val stopNameS: String,
    val stopNameE: String
)
