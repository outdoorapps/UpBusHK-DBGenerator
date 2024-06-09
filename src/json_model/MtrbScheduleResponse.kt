package json_model

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

data class MtrbScheduleResponse(
    val appRefreshTimeInSecond: String,
    val busStop: List<MTRBStop>,
    val caseNumberDetail: String,
    val footerRemarks: String?, // null if invalid
    val routeName: String,
    val routeStatusColour: String,
    val routeStatusTime: String,
    val status: String
) {
    companion object {
        fun fromJson(json: String) = Klaxon().parse<MtrbScheduleResponse>(json)
    }
}

data class MTRBStop(
    val bus: List<MTRBBus>, @Json(name = "busStopId") val busStopID: String, val isSuspended: String
)

data class MTRBBus(
    val arrivalTimeInSecond: String,
    val arrivalTimeText: String,
    @Json(name = "busId") val busID: String,
    val busLocation: BusLocation,
    val departureTimeInSecond: String,
    val departureTimeText: String,
    val isScheduled: String,
    val lineRef: String
)

data class BusLocation(
    val latitude: Double, val longitude: Double
)
