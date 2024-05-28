package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon


data class CtbRouteResponse(
    val type: String, val version: String,

    @Json(name = "generated_timestamp") val generatedTimestamp: String,

    val data: List<CtbRoute>
) {

    companion object {
        fun fromJson(json: String) = Klaxon().parse<CtbRouteResponse>(json)
    }
}

data class CtbRoute(
    val co: String, val route: String,

    @Json(name = "orig_tc") val origTc: String,

    @Json(name = "orig_en") val origEn: String,

    @Json(name = "dest_tc") val destTc: String,

    @Json(name = "dest_en") val destEn: String,

    @Json(name = "orig_sc") val origSc: String,

    @Json(name = "dest_sc") val destSc: String,

    @Json(name = "data_timestamp") val dataTimestamp: String
)
