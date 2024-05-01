package data

import com.beust.klaxon.Json

data class GovStop(@Json(index = 1) val stopId: Int, @Json(index = 2) val latLngCoord: List<Double>)