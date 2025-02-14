package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import util.KlaxonUtils.Companion.convert
import util.Region

@Suppress("UNCHECKED_CAST")
private val klaxon = Klaxon().convert(MinibusRouteList::class, {
    MinibusRouteList(
        routes = it.obj!!.obj("routes")!!.map { (k, v) -> Region.valueOf(k) to (v as List<String>) }.toMap(),
        dataTimestamp = it.objString("data_timestamp")
    )
}, { it.toString() })

data class MinibusRouteResponse(
    val type: String, val version: String,

    @Json(name = "generated_timestamp") val generatedTimestamp: String,

    val data: MinibusRouteList
) {
    companion object {
        fun fromJson(json: String) = klaxon.parse<MinibusRouteResponse>(json)
    }
}

data class MinibusRouteList(
    val routes: Map<Region, List<String>>,

    @Json(name = "data_timestamp") val dataTimestamp: String
)