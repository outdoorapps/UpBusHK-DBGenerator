package utils

import data.MiniBusRoute
import data.MiniBusStop
import data.RequestedMinibusData
import json_models.MinibusRouteInfoResponse
import json_models.MinibusRouteResponse
import json_models.MinibusRouteStopResponse
import json_models.MinibusStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utils.APIs.Companion.MINIBUS_ROUTE_STOP_URL
import utils.APIs.Companion.MINIBUS_ROUTE_URL
import utils.APIs.Companion.MINIBUS_STOP_URL
import utils.HttpUtils.Companion.get
import utils.HttpUtils.Companion.getAsync
import utils.MinibusHelper.Companion.fetchRoutesAndStopNames
import utils.MinibusHelper.Companion.fetchStopCoordinates
import utils.Paths.Companion.MINIBUS_EXPORT_PATH
import utils.Utils.Companion.execute
import utils.Utils.Companion.writeToGZ
import java.math.RoundingMode
import java.util.concurrent.CountDownLatch

class MinibusHelper {
    companion object {
        private val mutex = Mutex()

        fun fetchRoutesAndStopNames(requestedMinibusData: RequestedMinibusData) {
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
                                val stops =
                                    MinibusRouteStopResponse.fromJson(routeStopResponse)?.data?.routeStops //todo need to save stop names, check for name conflict
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
                                        requestedMinibusData.minibusRoutes.add(newRoute)
                                        if (!stops.isNullOrEmpty()) {
                                            stops.forEach { stop ->
                                                val existingStop =
                                                    requestedMinibusData.minibusStops.filter { x -> x.stopId == stop.stopID }
                                                if (existingStop.isEmpty()) {
                                                    requestedMinibusData.minibusStops.add(
                                                        MiniBusStop(
                                                            stopId = stop.stopID,
                                                            engName = stop.nameEn,
                                                            chiTName = stop.nameTc,
                                                            chiSName = stop.nameSc,
                                                            latLngCoord = listOf()
                                                        )
                                                    )
                                                } else {
                                                    // todo names are not standardized
//                                                    if (existingStop[0].chiTName != stop.nameTc) {
//                                                        println("different name for stopID ${existingStop[0].stopId}, 1:${existingStop[0].chiTName}, 2:${stop.nameTc}")
//                                                    }
                                                }
                                            }
                                        }
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
            requestedMinibusData.minibusRoutes.sortWith(compareBy({ it.number }, { it.bound }, { it.region }))
            println("Total minibus routes added (inbound & outbound): ${requestedMinibusData.minibusRoutes.size}")
            println("Total minibus stops: ${requestedMinibusData.minibusStops.size}")
        }

        fun fetchStopCoordinates(requestedMinibusData: RequestedMinibusData) {
            val newSet = mutableSetOf<MiniBusStop>()
            val totalCount = requestedMinibusData.minibusStops.size
            val countDownLatch = CountDownLatch(totalCount)
            val start = System.currentTimeMillis()

            requestedMinibusData.minibusStops.forEach { stop ->
                getAsync("$MINIBUS_STOP_URL/${stop.stopId}", onFailure = {
                    countDownLatch.countDown()
                    println("Request for minibus stopID $stop failed")
                }, onResponse = {
                    val data = MinibusStopResponse.fromJson(it)?.data
                    val lat = data?.coordinates?.wgs84?.latitude!!.toBigDecimal().setScale(5, RoundingMode.HALF_EVEN)
                        .toDouble()
                    val long =
                        data.coordinates.wgs84.longitude.toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
                    val newStop =
                        MiniBusStop(stop.stopId, stop.engName, stop.chiTName, stop.chiSName, listOf(lat, long))
                    CoroutineScope(Dispatchers.IO).launch {
                        mutex.withLock {
                            newSet.add(newStop)
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
                })

            }
            countDownLatch.await()
            println("Total minibus stops coordinates fetched: ${requestedMinibusData.minibusStops.size}")
        }
    }
}

/// Get route list -> get route info -> get route stops -> get stops coordinates
fun main() {
    val requestedMinibusData = RequestedMinibusData()
    execute("Getting minibus routes...", printOnNextLine = true) {
        fetchRoutesAndStopNames(requestedMinibusData)
    }

    execute("Getting minibus stops coordinates...", printOnNextLine = true) {
        fetchStopCoordinates(requestedMinibusData)
    }

    execute("Writing minibus data \"$MINIBUS_EXPORT_PATH\"...") {
        writeToGZ(requestedMinibusData.toJson(), MINIBUS_EXPORT_PATH)
    }
}