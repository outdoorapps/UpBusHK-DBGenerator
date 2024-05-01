package utils

import data.MiniBusRoute
import data.RequestedMinibusData
import json_models.MinibusRouteInfoResponse
import json_models.MinibusRouteResponse
import json_models.MinibusRouteStopResponse
import kotlinx.coroutines.sync.Mutex
import utils.HttpUtils.Companion.get
import utils.MinibusHelper.Companion.getRoutes
import utils.Paths.Companion.MINIBUS_EXPORT_PATH
import utils.Paths.Companion.MINIBUS_ROUTE_STOP_URL
import utils.Paths.Companion.MINIBUS_ROUTE_URL
import utils.Utils.Companion.execute
import utils.Utils.Companion.writeToGZ
import kotlin.time.Duration
import kotlin.time.measureTime

class MinibusHelper {
    companion object {
        private val mutex = Mutex()

        /// Get route list -> get route info -> get route stops -> get stops coordinates
        fun getRoutes(): List<MiniBusRoute> {
            var t = Duration.ZERO
            val list = mutableListOf<MiniBusRoute>()
            val response = get(MINIBUS_ROUTE_URL)
            val routes = MinibusRouteResponse.fromJson(response)?.data?.routes
            routes?.forEach { (region, routesInRegion) ->
                routesInRegion.forEach { number ->
                    var miniBusRoutes: List<MiniBusRoute>
                    val time = measureTime {
                        miniBusRoutes = getRoute(region, number)
                    }
                    t = t.plus(time)
                    miniBusRoutes.forEach {
                        list.add(it)
                        val count = list.size
                        if (count % 50 == 0) {
                            println("$count routes added in $t")
                        }
                    }
                }
            }
            list.sortWith(compareBy({ it.number }, { it.bound }, { it.region }))
            println("Total minibus routes: ${list.size}")
            return list
        }

        private fun getRoute(region: Region, number: String): List<MiniBusRoute> {
            val list = mutableListOf<MiniBusRoute>()
            val routeInfoResponse = get("$MINIBUS_ROUTE_URL/${region.value}/$number")
            val data = MinibusRouteInfoResponse.fromJson(routeInfoResponse)?.data
            if (!data.isNullOrEmpty()) {
                val routeInfo = data[0]
                routeInfo.directions.forEach { direction ->
                    val routeStopResponse = get("$MINIBUS_ROUTE_STOP_URL/${routeInfo.routeID}/${direction.routeSeq}")
                    val stops = MinibusRouteStopResponse.fromJson(routeStopResponse)?.data?.routeStops
                    val bound = if (direction.routeSeq == 1) Bound.O else Bound.I // RouteSeq can only be 1 or 2

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
    val requestedMinibusData = RequestedMinibusData()
    execute("Getting minibus routes...", printOnNextLine = true) {
        val routes = getRoutes()
        requestedMinibusData.minibusRoutes.addAll(routes)
        writeToGZ(requestedMinibusData.toJson(), MINIBUS_EXPORT_PATH)
    }
}