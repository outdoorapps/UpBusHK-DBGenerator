package data

import com.programmerare.crsTransformations.coordinate.CrsCoordinate
import json_models.RouteInfo

data class MappedRoute(val routeInfo: RouteInfo, val path: List<CrsCoordinate>)