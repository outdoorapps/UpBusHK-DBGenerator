package utils

import data.MiniBusRoute
import data.RequestedMinibusData
import json_models.MinibusRouteInfoResponse
import json_models.MinibusRouteResponse
import json_models.MinibusRouteStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utils.HttpUtils.Companion.get
import utils.HttpUtils.Companion.getAsync
import utils.MinibusHelper.Companion.getRoutes
import utils.Paths.Companion.MINIBUS_EXPORT_PATH
import utils.Paths.Companion.MINIBUS_ROUTE_STOP_URL
import utils.Paths.Companion.MINIBUS_ROUTE_URL
import utils.Utils.Companion.execute
import utils.Utils.Companion.writeToGZ
import java.util.concurrent.CountDownLatch

class MinibusHelper {
    companion object {
        private val mutex = Mutex()

        /// Get route list -> get route info -> get route stops -> get stops coordinates
        fun getRoutes(): List<MiniBusRoute> {
            val list = mutableListOf<MiniBusRoute>()
            val response = get(MINIBUS_ROUTE_URL)
            val routes = MinibusRouteResponse.fromJson(response)?.data?.routes

            var count = 0
            routes?.forEach { (_, routesInRegion) -> count += routesInRegion.size }
            val totalCount = count
            val countDownLatch = CountDownLatch(totalCount)
            val start = System.currentTimeMillis()

            routes?.forEach { (region, routesInRegion) ->
                routesInRegion.forEach { number ->
                    getAsync("$MINIBUS_ROUTE_URL/${region.value}/$number", onFailure = {
                        countDownLatch.countDown()
                        println("Request minibus info for $region $number failed")
                    }, onResponse = {
                        val data = MinibusRouteInfoResponse.fromJson(it)?.data
                        if (!data.isNullOrEmpty()) {
                            val routeInfo = data[0]
                            routeInfo.directions.forEach { direction ->
                                val routeStopResponse =
                                    get("$MINIBUS_ROUTE_STOP_URL/${routeInfo.routeID}/${direction.routeSeq}")
                                val stops = MinibusRouteStopResponse.fromJson(routeStopResponse)?.data?.routeStops
                                val bound = if (direction.routeSeq == 1) Bound.O else Bound.I  // Can only be1 or 2

                                val newRoute = MiniBusRoute(routeId = routeInfo.routeID,
                                    region = region,
                                    number = number,
                                    bound = bound,
                                    originEn = direction.origEn,
                                    originChiT = direction.origTc,
                                    originChiS = direction.origSc,
                                    destEn = direction.destEn,
                                    destChiT = direction.destTc,
                                    destChiS = direction.destSc,
                                    stops = if (stops.isNullOrEmpty()) listOf() else stops.map { stop -> stop.stopID })

                                CoroutineScope(Dispatchers.IO).launch {
                                    mutex.withLock {
                                        list.add(newRoute)
                                    }
                                }
                            }
                        }
                        countDownLatch.countDown()
                        CoroutineScope(Dispatchers.IO).launch {
                            mutex.withLock {
                                val finishCount = totalCount - countDownLatch.count.toInt()
                                if (finishCount % 50 == 0) {
                                    Utils.printPercentage(finishCount, totalCount, start)
                                }
                            }
                        }
                    }) //async
                }
            }
            countDownLatch.await()
            list.sortWith(compareBy({ it.number }, { it.bound }, { it.region }))
            println("Total minibus routes added (inbound & outbound): ${list.size}")
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