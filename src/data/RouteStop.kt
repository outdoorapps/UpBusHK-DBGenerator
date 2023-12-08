package data

import Bound
import Company

data class RouteStop(
    val company: Company,
    val number: String,
    val bound: Bound?, // kmb only
    val routeId: String?, // nlb only
)
