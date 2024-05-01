package json_models

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class MinibusStopRouteResponse(
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: List<MinibusStopRouteData>
) {
    fun toJson() = Klaxon().toJsonString(this)

    companion object {
        fun fromJson(json: String) = Klaxon().parse<MinibusStopRouteResponse>(json)
    }
}

data class MinibusStopRouteData(
    @Json(name = "route_id")
    val routeID: Long,

    @Json(name = "route_seq")
    val routeSeq: Long,

    @Json(name = "stop_seq")
    val stopSeq: Long,

    @Json(name = "name_tc")
    val nameTc: String,

    @Json(name = "name_sc")
    val nameSc: String,

    @Json(name = "name_en")
    val nameEn: String
)
