package json_models

import com.beust.klaxon.*

private val klaxon = Klaxon()

data class MinibusStopResponse (
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: MinibusStopData
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<MinibusStopResponse>(json)
    }
}

data class MinibusStopData (
    val coordinates: Coordinates,
    val enabled: Boolean,

    @Json(name = "remarks_tc")
    val remarksTc: Any? = null,

    @Json(name = "remarks_sc")
    val remarksSc: Any? = null,

    @Json(name = "remarks_en")
    val remarksEn: Any? = null,

    @Json(name = "data_timestamp")
    val dataTimestamp: String
)

data class Coordinates (
    val wgs84: Hk80,
    val hk80: Hk80
)

data class Hk80 (
    val latitude: Double,
    val longitude: Double
)