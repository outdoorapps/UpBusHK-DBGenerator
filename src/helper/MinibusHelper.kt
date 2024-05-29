package helper

import com.github.houbb.opencc4j.util.ZhConverterUtil
import data.MiniBusRoute
import data.MinibusData
import data.MinibusStop
import getMinibusData
import json_model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import util.APIs.Companion.MINIBUS_ROUTE_STOP_URL
import util.APIs.Companion.MINIBUS_ROUTE_URL
import util.APIs.Companion.MINIBUS_STOP_ROUTE_URL
import util.APIs.Companion.MINIBUS_STOP_URL
import util.Bound
import util.HttpUtils.Companion.get
import util.HttpUtils.Companion.getAsync
import util.TransportType
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.loadGovStop
import util.Utils.Companion.roundCoordinate
import util.Utils.Companion.standardizeChiStopName
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

        val stopIDsNeededLatLng = mutableSetOf<Int>()
        val stopIDLatLngMap = mutableMapOf<Int, List<Double>>()
        execute("Matching minibus stops data...") {
            minibusData.minibusRoutes.forEach {
                stopIDsNeededLatLng.addAll(it.stops)
            }

            val minibusStopsOnFile = loadGovStop(TransportType.MINIBUS)

            stopIDsNeededLatLng.forEach {
                val latLng = minibusStopsOnFile.find { stopOnFile -> stopOnFile.stopId == it }?.coordinate
                if (latLng != null) stopIDLatLngMap[it] = latLng
            }
            stopIDsNeededLatLng.removeAll(stopIDLatLngMap.keys)
        }
        println("Minibus stops on file: ${stopIDLatLngMap.size}, not on file: ${stopIDsNeededLatLng.size}")

        if (stopIDsNeededLatLng.isNotEmpty()) {
            execute("Requesting minibus stop coordinates not on file...") {
                do {
                    stopIDsNeededLatLng.forEach {
                        val response = get("$MINIBUS_STOP_URL/$it")
                        val data = MinibusStopResponse.fromJson(response)?.data
                        val lat = data?.coordinates?.wgs84?.latitude!!.roundCoordinate()
                        val long = data.coordinates.wgs84.longitude.roundCoordinate()
                        stopIDLatLngMap[it] = listOf(lat, long)
                    }
                    stopIDsNeededLatLng.removeAll(stopIDLatLngMap.keys)
                } while (stopIDsNeededLatLng.isNotEmpty())
            }
        }

        execute("Getting minibus stops info...", printOnNextLine = true) {
            minibusData.minibusStops.addAll(getStops(stopIDLatLngMap))
        }
        println("Minibus routes added: ${minibusData.minibusRoutes.size}, minibus stops added: ${minibusData.minibusStops.size}")
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
                                originChiS = ZhConverterUtil.toSimple(direction.origTc),
                                destEn = direction.destEn,
                                destChiT = direction.destTc,
                                destChiS = ZhConverterUtil.toSimple(direction.destTc),
                                fullFare = 0.0, // todo
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
        println("Minibus routes added (inbound & outbound): ${list.size}")
        return list
    }

    private fun getStops(stopIDLatLngMap: Map<Int, List<Double>>): List<MinibusStop> {
        this.stopIDs.clear()
        this.stopIDs.addAll(stopIDLatLngMap.keys)
        val list = mutableListOf<MinibusStop>()

        do {
            list.addAll(getStopsAsync(stopIDLatLngMap))
            val size = this.stopIDs.size
            if (size != 0) {
                println("$size errors received, waiting for ${TIMEOUT_SECOND}s before restarting...")
                Thread.sleep(TIMEOUT_SECOND * 1000)
                println("Restarting...")
            }
        } while (this.stopIDs.isNotEmpty())
        list.sortBy { it.stopId }
        return list
    }

    private fun getStopsAsync(stopIDLatLngMap: Map<Int, List<Double>>): List<MinibusStop> {
        val list = mutableListOf<MinibusStop>()
        val totalCount = stopIDs.size
        val countDownLatch = CountDownLatch(totalCount)
        val start = System.currentTimeMillis()

        stopIDs.forEach { stopId ->
            getAsync("$MINIBUS_STOP_ROUTE_URL/$stopId", onFailure = {
                countDownLatch.countDown()
                // println("Request for minibus stopID $stopId failed")
            }, onResponse = { response ->
                var newStop: MinibusStop? = null
                val stopRouteData = MinibusStopRouteResponse.fromJson(response)?.data
                if (!stopRouteData.isNullOrEmpty()) {
                    // Choose the name set with the shortest standardize traditional Chinese name
                    val nameSet = stopRouteData.sortedBy { it.nameTc.standardizeChiStopName().length }[0]
                    val nameTc = nameSet.nameTc.standardizeChiStopName()

                    // Always generate simplified Chinese (Server fills it with traditional or is missing)
                    val nameSc = ZhConverterUtil.toSimple(nameTc)
                    newStop = MinibusStop(stopId, nameSet.nameEn, nameTc, nameSc, stopIDLatLngMap[stopId]!!)
                }
                CoroutineScope(Dispatchers.IO).launch {
                    mutex.withLock {
                        if (newStop != null) {
                            list.add(newStop)
                            stopIDs.remove(stopId)
                        }
                        countDownLatch.countDown()
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