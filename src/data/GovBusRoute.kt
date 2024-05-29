package data

import com.beust.klaxon.*

@Target(AnnotationTarget.FIELD)
annotation class StopFareMap

data class GovBusRoute @JvmOverloads constructor(
    @Json(index = 1) val routeId: Int,
    @Json(index = 2) val routeSeq: Int,
    @Json(index = 3) val companyCode: String,
    @Json(index = 4) val routeNameE: String,
    @Json(index = 5) val stStopNameE: String,
    @Json(index = 6) val stStopNameC: String,
    @Json(index = 7) val stStopNameS: String,
    @Json(index = 8) val edStopNameE: String,
    @Json(index = 9) val edStopNameC: String,
    @Json(index = 10) val edStopNameS: String,
    @Json(index = 11) val serviceMode: String,
    @Json(index = 12) val specialType: Int,
    @Json(index = 13) val journeyTime: Int,
    @Json(index = 14) val fullFare: Double,
    @StopFareMap @Json(index = 15) val stopFareMap: Map<Int, Double?> = emptyMap()
) {
    companion object {
        fun fromJson(json: String) =
            Klaxon().fieldConverter(StopFareMap::class, stopFareMapConverter).parse<GovBusData>(json)
    }

    fun toJson() = Klaxon().toJsonString(this)
}

val stopFareMapConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == Map::class.java

    override fun toJson(value: Any): String = value.toString()

    override fun fromJson(jv: JsonValue) = if (jv.obj != null) {
        // *The normal Klaxon().parseFromJsonObject<Map<String, Double?>>(jv.obj!!) changes the order of stops
        jv.obj!!.map.entries.associate { it.key.toInt() to it.value }
    } else {
        throw KlaxonException("Couldn't parse null StopFareMap")
    }
}