package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import util.Bound
import util.KlaxonUtils.Companion.convert

private val klaxon = Klaxon().convert(Bound::class, { Bound.valueOf(it.string!!) }, { "\"${it.name}\"" })

data class KmbRouteResponse(
    val type: String, val version: String,

    @Json(name = "generated_timestamp") val generatedTimestamp: String,

    val data: List<KmbRoute>
) {
    companion object {
        fun fromJson(json: String) = klaxon.parse<KmbRouteResponse>(json)
    }
}

data class KmbRoute(
    val route: String, val bound: Bound,

    @Json(name = "service_type") val serviceType: String,

    @Json(name = "orig_en") val origEn: String,

    @Json(name = "orig_tc") val origTc: String,

    @Json(name = "orig_sc") val origSc: String,

    @Json(name = "dest_en") val destEn: String,

    @Json(name = "dest_tc") val destTc: String,

    @Json(name = "dest_sc") val destSc: String
)
