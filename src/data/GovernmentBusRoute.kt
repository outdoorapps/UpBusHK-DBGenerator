package data

data class GovernmentBusRoute(
    val routeId: Int,
    val routeSeq: Int,
    val companyCode: String,
    val routeNameE: String,
    val stStopId: Int,
    val stStopNameE: String,
    val stStopNameC: String,
    val stStopNameS: String,
    val edStopId: Int,
    val edStopNameE: String,
    val edStopNameC: String,
    val edStopNameS: String,
    val serviceMode: String,
    val specialType: Int,
    val journeyTime: Int,
    val fullFare: Double,
)