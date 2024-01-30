package json_models

// To parse the JSON, install Klaxon and do:
//
//   val ctbRouteStopResponse = CtbRouteStopResponse.fromJson(jsonString)
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import data.CRS

private val klaxon = Klaxon()

data class GovStopRaw(
    val type: String, val name: String, val crs: CRS, val features: List<Feature>
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = klaxon.parse<GovStopRaw>(json)
    }
}

data class Feature(
    val type: String, val geometry: BusStopRawGeometry, val properties: FeatureProperties
)

data class BusStopRawGeometry(
    val type: String, val coordinates: List<Int>
)

data class FeatureProperties(
    @Json(name = "OBJECTID") val objectid: Int,
    @Json(name = "STOP_ID") val stopId: Int,
    @Json(name = "LAST_UPDATE_DATE") val lastUpdateDate: String
)
