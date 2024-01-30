package json_models

// To parse the JSON, install Klaxon and do:
//
//   val kmbRouteStop = KmbRouteStop.fromJson(jsonString)

import utils.Bound
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class KmbRouteStopResponse(
    val type: String,
    val version: String,
    @Json(name = "generated_timestamp") val generatedTimestamp: String,
    @Json(name = "data") val stops: List<KmbRouteStop>
) {
    fun toJson() = Klaxon().toJsonString(this)

    companion object {
        fun fromJson(json: String) = Klaxon().parse<KmbRouteStopResponse>(json)
    }
}

data class KmbRouteStop(
    val route: String,
    val bound: Bound,
    @Json(name = "service_type") val serviceType: String,
    val seq: String,
    val stop: String
)

