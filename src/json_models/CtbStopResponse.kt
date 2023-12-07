package json_models

import com.beust.klaxon.Json
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class CtbStopResponse(
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: CtbStop?
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<CtbStopResponse>(json)
    }
}

data class CtbStop(
    val stop: String? = null,

    @Json(name = "name_tc")
    val nameTc: String? = null,

    @Json(name = "name_en")
    val nameEn: String? = null,

    val lat: String? = null,
    val long: String? = null,

    @Json(name = "name_sc")
    val nameSc: String? = null,

    @Json(name = "data_timestamp")
    val dataTimestamp: String? = null
)
