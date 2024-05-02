package helpers

import com.beust.klaxon.KlaxonException
import com.github.houbb.opencc4j.util.ZhConverterUtil
import data.MiniBusRoute
import data.MinibusData
import data.MinibusStop
import getMinibusData
import json_models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utils.APIs.Companion.MINIBUS_ROUTE_STOP_URL
import utils.APIs.Companion.MINIBUS_ROUTE_URL
import utils.APIs.Companion.MINIBUS_STOP_ROUTE_URL
import utils.APIs.Companion.MINIBUS_STOP_URL
import utils.Bound
import utils.HttpUtils.Companion.get
import utils.HttpUtils.Companion.getAsync
import utils.Utils
import utils.Utils.Companion.execute
import utils.Utils.Companion.roundLatLng
import utils.Utils.Companion.trimIdeographicSpace
import java.util.concurrent.CountDownLatch

class MinibusHelper {
    companion object {
        private const val TIMEOUT_SECOND = 120L
    }

    private val mutex = Mutex()
    private val stopIDs = mutableSetOf<Int>()

    /// Get route list -> get route info -> get route stops -> get stops coordinates
    fun getMinibusData(): MinibusData {
        val minibusData = MinibusData()
        execute("Getting minibus routes...", printOnNextLine = true) {
            minibusData.minibusRoutes.addAll(getRoutes())
        }

        execute("Getting minibus stops coordinates...", printOnNextLine = true) {
            val stopIDs = mutableSetOf<Int>()
            minibusData.minibusRoutes.forEach {
                stopIDs.addAll(it.stops)
            }
            minibusData.minibusStops.addAll(getStops(stopIDs))
        }
        return minibusData
    }

    private fun getRoutes(): List<MiniBusRoute> {
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

    private fun getStops(stopIDs: Set<Int>): List<MinibusStop> {
        this.stopIDs.clear()
        this.stopIDs.addAll(stopIDs)
        val list = mutableListOf<MinibusStop>()

        do {
            list.addAll(getStopsAsync())
            if (stopIDs.isNotEmpty()) {
                println("${this.stopIDs.size} errors received, waiting for ${TIMEOUT_SECOND}s before restarting requests")
                Thread.sleep(TIMEOUT_SECOND * 1000)
                println("Restarting...")
            }
        } while (this.stopIDs.isNotEmpty())
        list.sortBy { it.stopId }
        println("Total minibus stops: ${list.size}")
        return list
    }

    private fun getStopsAsync(): List<MinibusStop> {
        val list = mutableListOf<MinibusStop>()
        val totalCount = stopIDs.size
        val countDownLatch = CountDownLatch(totalCount)
        val start = System.currentTimeMillis()

        stopIDs.forEach { stopId ->
            getAsync("$MINIBUS_STOP_URL/${stopId}", onFailure = {
                countDownLatch.countDown()
                // println("Request for minibus stopID $stopId failed")
            }, onResponse = { minibusStopResponse ->
                val data = MinibusStopResponse.fromJson(minibusStopResponse)?.data
                val lat = data?.coordinates?.wgs84?.latitude!!.roundLatLng()
                val long = data.coordinates.wgs84.longitude.roundLatLng()

                val response = get("$MINIBUS_STOP_ROUTE_URL/$stopId")
                try {
                    val stopRouteData = MinibusStopRouteResponse.fromJson(response)?.data

                    if (!stopRouteData.isNullOrEmpty()) {
                        // Remove "\u3000" ideographic space
                        // Then, choose the name set with the shortest traditional Chinese name
                        val nameSet = stopRouteData.sortedBy { it.nameTc.trimIdeographicSpace().length }[0]
                        val nameTc = nameSet.nameTc.trimIdeographicSpace()

                        // Always convert because sometimes the server filled it with tradition Chinese or is missing
                        val nameSc = ZhConverterUtil.toSimple(nameTc)

                        val newStop = MinibusStop(stopId, nameSet.nameEn, nameTc, nameSc, listOf(lat, long))
                        CoroutineScope(Dispatchers.IO).launch {
                            mutex.withLock {
                                list.add(newStop)
                                stopIDs.remove(stopId)
                            }
                        }
                    }
                } catch (_: KlaxonException) {
                }
                countDownLatch.countDown()
                CoroutineScope(Dispatchers.IO).launch {
                    mutex.withLock {
                        val finishCount = totalCount - countDownLatch.count.toInt()
                        if (finishCount % 100 == 0) {
                            Utils.printPercentage(finishCount, totalCount, start)
                        }
                    }
                }
            })
        }
        countDownLatch.await()
        return list
    }
}

fun main() {
    getMinibusData()
}