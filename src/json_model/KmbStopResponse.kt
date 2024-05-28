package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class KmbStopResponse(
    val type: String, val version: String,

    @Json(name = "generated_timestamp") val generatedTimestamp: String,

    val data: List<KmbStop>
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<KmbStopResponse>(json)
    }
}

data class KmbStop(
    val stop: String,

    @Json(name = "name_en") val nameEn: String,

    @Json(name = "name_tc") val nameTc: String,

    @Json(name = "name_sc") val nameSc: String,

    val lat: String, val long: String
)
