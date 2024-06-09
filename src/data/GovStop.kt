package data

import com.beust.klaxon.Json

data class GovStop(
    @Json(index = 1) val stopId: Int,
    @Json(index = 2) val stopNameE: String,
    @Json(index = 3) val stopNameC: String,
    @Json(index = 4) val stopNameS: String,
    @Json(index = 5) val coordinate: List<Double>,
)