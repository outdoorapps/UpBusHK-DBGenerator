package json_model

import com.beust.klaxon.Json

data class CRS(
    val type: String, val properties: CRSProperties
)

data class CRSProperties(
    val name: String
)

data class LongLatGeometry(
    val type: String, @Json(name = "coordinates") val longLatCoordinates: List<Double>
)

data class Wgs84Geometry(
    val type: String, @Json(name = "coordinates") val coordinates: List<Double>
)