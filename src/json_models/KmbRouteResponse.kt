package json_models

import utils.Bound
import com.beust.klaxon.Converter
import com.beust.klaxon.Json
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon

private fun <T> Klaxon.convert(
    k: kotlin.reflect.KClass<*>,
    fromJson: (JsonValue) -> T,
    toJson: (T) -> String,
    isUnion: Boolean = false
) =
    this.converter(object : Converter {
        @Suppress("UNCHECKED_CAST")
        override fun toJson(value: Any) = toJson(value as T)
        override fun fromJson(jv: JsonValue) = fromJson(jv) as Any
        override fun canConvert(cls: Class<*>) = cls == k.java || (isUnion && cls.superclass == k.java)
    })

private val klaxon = Klaxon()
    .convert(Bound::class, { Bound.fromValue(it.string!!) }, { "\"${it.value}\"" })

data class KmbRouteResponse(
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: List<KmbRoute>
) {
    public fun toJson() = klaxon.toJsonString(this)

    companion object {
        public fun fromJson(json: String) = klaxon.parse<KmbRouteResponse>(json)
    }
}

data class KmbRoute(
    val route: String,
    val bound: Bound,

    @Json(name = "service_type")
    val serviceType: String,

    @Json(name = "orig_en")
    val origEn: String,

    @Json(name = "orig_tc")
    val origTc: String,

    @Json(name = "orig_sc")
    val origSc: String,

    @Json(name = "dest_en")
    val destEn: String,

    @Json(name = "dest_tc")
    val destTc: String,

    @Json(name = "dest_sc")
    val destSc: String
)
