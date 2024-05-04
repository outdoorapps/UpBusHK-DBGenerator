package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class MinibusStopResponse(
    val type: String, val version: String,

    @Json(name = "generated_timestamp") val generatedTimestamp: String,

    val data: MinibusStopData
) {
    fun toJson() = Klaxon().toJsonString(this)

    companion object {
        fun fromJson(json: String) = Klaxon().parse<MinibusStopResponse>(json)
    }
}

data class MinibusStopData(
    val coordinates: Coordinates, val enabled: Boolean,

    @Json(name = "remarks_tc") val remarksTc: String? = null,

    @Json(name = "remarks_sc") val remarksSc: String? = null,

    @Json(name = "remarks_en") val remarksEn: String? = null,

    @Json(name = "data_timestamp") val dataTimestamp: String
)

data class Coordinates(
    val wgs84: Hk80, val hk80: Hk80
)

data class Hk80(
    val latitude: Double, val longitude: Double
)
