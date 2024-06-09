package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class CtbStopResponse(
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: CtbStop?
) {
    fun toJson() = Klaxon().toJsonString(this)

    companion object {
        fun fromJson(json: String) = Klaxon().parse<CtbStopResponse>(json)
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
