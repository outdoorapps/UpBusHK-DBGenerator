package data

import com.beust.klaxon.Json

data class GovBusRoute(
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
    @Json(index = 15) val stopFareMap: Map<Int, Double?>, // Seq to StopID
)