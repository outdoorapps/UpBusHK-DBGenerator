package json_model

// To parse the JSON, install Klaxon and do:
//
//   val ctbRouteStopResponse = CtbRouteStopResponse.fromJson(jsonString)
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class GovStopCollection(
    val type: String, val name: String, val crs: CRS, val features: List<Feature>
) {

    companion object {
        fun fromJson(json: String) = Klaxon().parse<GovStopCollection>(json)
    }
}

data class Feature(
    val type: String, val geometry: StopGeometry, val properties: FeatureProperties
)

data class StopGeometry(
    val type: String, @Json(name = "coordinates") val hk1980Coordinates: List<Int>
)

data class FeatureProperties(
    @Json(name = "OBJECTID") val objectid: Int,
    @Json(name = "STOP_ID") val stopId: Int,
    @Json(name = "LAST_UPDATE_DATE") val lastUpdateDate: String
)
