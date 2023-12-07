package json_models

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class KmbStopResponse(
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: List<KmbStop>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<KmbStopResponse>(json)
    }
}

data class KmbStop(
    val stop: String,

    @Json(name = "name_en")
    val nameEn: String,

    @Json(name = "name_tc")
    val nameTc: String,

    @Json(name = "name_sc")
    val nameSc: String,

    val lat: String,
    val long: String
)
