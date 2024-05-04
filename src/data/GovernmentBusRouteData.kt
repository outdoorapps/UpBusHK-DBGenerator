package data

import com.beust.klaxon.*

@Target(AnnotationTarget.FIELD)
annotation class IntMap

data class GovernmentBusRouteData @JvmOverloads constructor(
    @Json(name = "government_bus_routes", index = 1)
    val governmentBusRoutes: MutableList<GovernmentBusRoute> = mutableListOf(),
    @IntMap @Json(name = "government_bus_stop_coordinates", index = 2)
    val governmentBusStopCoordinates: MutableMap<Int, List<Double>> = mutableMapOf()
) {
    companion object {
        fun fromJson(json: String) =
            Klaxon().fieldConverter(IntMap::class, mapConverter).parse<GovernmentBusRouteData>(json)
    }

    fun toJson() = Klaxon().toJsonString(this)

}

val mapConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == Map::class.java

    override fun toJson(value: Any): String = value.toString()

    override fun fromJson(jv: JsonValue) = if (jv.obj != null) {
        val stringMap = Klaxon().parseFromJsonObject<MutableMap<String, List<Double>>>(jv.obj!!)
        stringMap!!.entries.associate { it.key.toInt() to it.value }
    } else {
        throw KlaxonException("Couldn't parse null IntMap")
    }
}