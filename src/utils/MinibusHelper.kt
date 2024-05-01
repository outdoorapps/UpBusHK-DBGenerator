package utils

import data.MiniBusRoute
import json_models.MinibusRouteInfoResponse
import json_models.MinibusRouteResponse
import json_models.MinibusRouteStopResponse
import utils.HttpUtils.Companion.get
import utils.MinibusHelper.Companion.getRoutes
import utils.Paths.Companion.MINIBUS_ROUTE_STOP_URL
import utils.Paths.Companion.MINIBUS_ROUTE_URL

class MinibusHelper {
    companion object {
        /// Get route list -> get route info -> get route stops -> get stops coordinates
        fun getRoutes(): List<MiniBusRoute> {
            val list = mutableListOf<MiniBusRoute>()
            val response = get(MINIBUS_ROUTE_URL)
            val routes = MinibusRouteResponse.fromJson(response)?.data?.routes //todo sort them
            routes?.forEach { (region, routesInRegion) ->
                routesInRegion.forEach { number ->
                    val miniBusRoutes = getRoute(region, number)
                    list.addAll(miniBusRoutes)
                    miniBusRoutes.forEach { println("Added route ${it.routeId} bound: ${it.bound}, count: ${list.size}") }
                }
            }
            return list
        }

        private fun getRoute(region: Region, number: String): List<MiniBusRoute> {
            val list = mutableListOf<MiniBusRoute>()
            println("working on $number in $region")// todo
            val routeInfoResponse = get("$MINIBUS_ROUTE_URL/${region.value}/$number")
            val data = MinibusRouteInfoResponse.fromJson(routeInfoResponse)?.data
            if (!data.isNullOrEmpty()) {
                val routeInfo = data[0]
                routeInfo.directions.forEach { direction ->
                    val routeStopResponse = get("$MINIBUS_ROUTE_STOP_URL/${routeInfo.routeID}/${direction.routeSeq}")
                    val stops = MinibusRouteStopResponse.fromJson(routeStopResponse)?.data?.routeStops
                    val bound = if (direction.routeSeq == 1) Bound.O else Bound.I
                    //todo check if 1 and 2 are the only possible values
                    if (direction.routeSeq > 2) print(routeInfo.routeID)

                    list.add(
                        MiniBusRoute(routeId = routeInfo.routeID,
                            region = region,
                            number = number,
                            bound = bound,
                            originEn = direction.origEn,
                            originChiT = direction.origTc,
                            originChiS = direction.origSc,
                            destEn = direction.destEn,
                            destChiT = direction.destTc,
                            destChiS = direction.destSc,
                            stops = if (stops.isNullOrEmpty()) listOf() else stops.map { it.stopID })
                    )
                }
            }
            return list
        }
    }
}

fun main() {
    getRoutes()
}