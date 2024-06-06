package data

import com.beust.klaxon.*

@Target(AnnotationTarget.FIELD)
annotation class StopFarePairs

data class GovBusRoute @JvmOverloads constructor(
    @Json(index = 1) val routeId: Int,
    @Json(index = 2) val routeSeq: Int,
    @Json(index = 3) val companyCode: String,
    @Json(index = 4) val routeNameE: String,
    @Json(index = 5) val originEn: String,
    @Json(index = 6) val originChiT: String,
    @Json(index = 7) val originChiS: String,
    @Json(index = 8) val destEn: String,
    @Json(index = 9) val destChiT: String,
    @Json(index = 10) val destChiS: String,
    @Json(index = 11) val serviceMode: String,
    @Json(index = 12) val specialType: Int,
    @Json(index = 13) val journeyTime: Int,
    @Json(index = 14) val fullFare: Double,
    @StopFarePairs @Json(index = 15) val stopFarePairs: List<Pair<Int, Double?>> = emptyList()
) {
    companion object {
        fun fromJson(json: String) =
            Klaxon().fieldConverter(StopFarePairs::class, pairsConverter).parse<GovBusData>(json)
    }

    fun toJson() = Klaxon().toJsonString(this)
}

val pairsConverter = object : Converter {
    override fun canConvert(cls: Class<*>) = cls == Pair::class.java

    override fun toJson(value: Any): String = value.toString()

    override fun fromJson(jv: JsonValue) = if (jv.array != null) {
        jv.array!!.value.map { jsonObject ->
            val obj = jsonObject as Map<*, *>
            val first = obj["first"].toString().toInt()
            val second = if (obj["second"] == null) null else obj["second"].toString().toDouble()
            first to second
        }
    } else {
        throw KlaxonException("Couldn't parse null StopFarePairs")
    }
}