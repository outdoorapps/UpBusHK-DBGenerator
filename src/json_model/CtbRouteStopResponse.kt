package json_model

// To parse the JSON, install Klaxon and do:
//
//   val ctbRouteStopResponse = CtbRouteStopResponse.fromJson(jsonString)

import util.Bound
import util.Company
import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class CtbRouteStopResponse(
    val type: String,
    val version: String,
    @Json(name = "generated_timestamp") val generatedTimestamp: String,
    @Json(name = "data") val stops: List<CtbRouteStop>
) {
    fun toJson() = Klaxon().toJsonString(this)

    companion object {
        fun fromJson(json: String) = Klaxon().parse<CtbRouteStopResponse>(json)
    }
}

data class CtbRouteStop(
    val co: Company,
    val route: String,
    val dir: Bound,
    val seq: Long,
    val stop: String,
    @Json(name = "data_timestamp") val dataTimestamp: String
)
