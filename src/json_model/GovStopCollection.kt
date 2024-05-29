package json_model

// To parse the JSON, install Klaxon and do:
//
//   val ctbRouteStopResponse = CtbRouteStopResponse.fromJson(jsonString)
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class GovStopCollection(
    val type: String, val name: String, val crs: CRS, val features: List<GovStopFeature>
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<GovStopCollection>(json)
    }
}

data class GovStopFeature(
    val type: String, val geometry: HK1980Geometry, val properties: FeatureProperties
)

data class FeatureProperties(
    @Json(name = "OBJECTID") val objectid: Int,
    @Json(name = "STOP_ID") val stopId: Int,
    @Json(name = "LAST_UPDATE_DATE") val lastUpdateDate: String
)
