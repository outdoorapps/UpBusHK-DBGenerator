package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class MinibusRouteStopResponse(
    val type: String, val version: String,

    @Json(name = "generated_timestamp") val generatedTimestamp: String,

    val data: Data
) {
    public fun toJson() = Klaxon().toJsonString(this)

    companion object {
        public fun fromJson(json: String) = Klaxon().parse<MinibusRouteStopResponse>(json)
    }
}

data class Data(
    @Json(name = "route_stops") val routeStops: List<RouteStop>,

    @Json(name = "data_timestamp") val dataTimestamp: String
)

data class RouteStop(
    @Json(name = "stop_seq") val stopSeq: Int,

    @Json(name = "stop_id") val stopID: Int,

    @Json(name = "name_tc") val nameTc: String,

    @Json(name = "name_sc") val nameSc: String,

    @Json(name = "name_en") val nameEn: String
)
