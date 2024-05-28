package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class NlbRouteStopResponse(
    val stops: List<NlbStop>
) {

    companion object {
        fun fromJson(json: String) = Klaxon().parse<NlbRouteStopResponse>(json)
    }
}

data class NlbStop(
    @Json(name = "stopId") val stop: String,
    @Json(name = "stopName_c") val stopNameC: String,
    @Json(name = "stopName_s") val stopNameS: String,
    @Json(name = "stopName_e") val stopNameE: String,
    @Json(name = "stopLocation_c") val stopLocationC: String,
    @Json(name = "stopLocation_s") val stopLocationS: String,
    @Json(name = "stopLocation_e") val stopLocationE: String,
    val latitude: String,
    val longitude: String,
    val fare: String,
    val fareHoliday: String,
    val someDepartureObserveOnly: Long
)
