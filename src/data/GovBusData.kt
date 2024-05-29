package data

import com.beust.klaxon.*

@Target(AnnotationTarget.FIELD)
annotation class CoordinateMap

data class GovBusData @JvmOverloads constructor(
    @Json(name = "gov_bus_routes", index = 1) val govBusRoutes: MutableList<GovBusRoute> = mutableListOf(),
    @CoordinateMap @Json(name = "gov_bus_stop_coordinates", index = 2)
    val govBusStopCoordinates: MutableMap<Int, List<Double>> = mutableMapOf()
) {
    companion object {
        fun fromJson(json: String) = Klaxon().fieldConverter(CoordinateMap::class, coordinateMapConverter)
            .fieldConverter(StopFareMap::class, stopFareMapConverter).parse<GovBusData>(json)
    }

    fun toJson() = Klaxon().toJsonString(this)
}

val coordinateMapConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == Map::class.java

    override fun toJson(value: Any): String = value.toString()

    override fun fromJson(jv: JsonValue) = if (jv.obj != null) {
        val stringMap = Klaxon().parseFromJsonObject<MutableMap<String, List<Double>>>(jv.obj!!)
        stringMap!!.entries.associate { it.key.toInt() to it.value }
    } else {
        throw KlaxonException("Couldn't parse null CoordinateMap")
    }
}