package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class NlbRouteResponse(
    val routes: List<NlbRoute>
) {

    companion object {
        fun fromJson(json: String) = Klaxon().parse<NlbRouteResponse>(json)
    }
}

data class NlbRoute(
    val routeId: String, val routeNo: String,

    @Json(name = "routeName_c") val routeNameC: String,

    @Json(name = "routeName_s") val routeNameS: String,

    @Json(name = "routeName_e") val routeNameE: String,

    val overnightRoute: Long, val specialRoute: Long
)
