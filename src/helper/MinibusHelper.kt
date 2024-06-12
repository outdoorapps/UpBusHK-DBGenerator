package helper

import GovDataParser
import com.beust.klaxon.KlaxonException
import com.github.houbb.opencc4j.util.ZhConverterUtil
import data.MiniBusRoute
import data.MinibusData
import data.MinibusStop
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
import util.Paths.Companion.MINIBUS_DATA_EXPORT_PATH
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.roundCoordinate
import util.Utils.Companion.standardizeChiStopName
import util.Utils.Companion.writeToGZ
import java.util.concurrent.CountDownLatch

class MinibusHelper {
    companion object {
        private const val TIMEOUT_SECOND = 120L
    }

    private val mutex = Mutex()
    private val stopIDs = mutableSetOf<String>()

    fun getMinibusData(exportToFile: Boolean, exportIntermediates: Boolean): MinibusData {
        // 1. Get all minibus routes
        lateinit var minibusRoutes: List<MiniBusRoute>
        val minibusStops = mutableListOf<MinibusStop>()
        execute("Getting minibus routes...", printOnNextLine = true) {
            minibusRoutes = getRoutes()
        }

        // 2. Get all stops that needed info
        val stopIDsNeededInfo = mutableSetOf<String>()
        minibusRoutes.forEach {
            stopIDsNeededInfo.addAll(it.stops)
        }

        // 3. Parse government stop data on file & add fare info
        val govMinibusData =
            GovDataParser.getGovMiniBusData(loadExistingData = false, exportToFile = exportIntermediates)
        minibusRoutes = minibusRoutes.map {
            val matchingRoute =
                govMinibusData.minibusRoutes.find { e -> it.number == e.number && it.bound == e.bound && it.region == e.region }
            if (matchingRoute != null) {
                it.copy(fullFare = matchingRoute.fullFare)
            } else {
                it
            }
        }

        // 4. Match government data to stops needed info
        execute("Matching minibus stops data...") {
            stopIDsNeededInfo.forEach {
                val stop = govMinibusData.minibusStops.find { stopOnFile -> stopOnFile.stopId == it }
                if (stop != null) minibusStops.add(stop)
            }
            minibusStops.forEach { stopIDsNeededInfo.remove(it.stopId) }
        }
        println("- Minibus stops on file: ${minibusStops.size}, not on file: ${stopIDsNeededInfo.size}")

        // 5. Request stops info not on file
        if (stopIDsNeededInfo.isNotEmpty()) {
            execute("Getting minibus stops info not on file...") {
                minibusStops.addAll(getStops(stopIDsNeededInfo))
            }
        }
        minibusStops.sortBy { it.stopId }
        val roundedMinibusStops =
            minibusStops.map { stop -> stop.copy(coordinate = stop.coordinate.map { it.roundCoordinate() }) }
        println("- Total minibus routes: ${minibusRoutes.size}, total minibus stops: ${minibusStops.size}")

        // 6. Export to file if needed
        val minibusData = MinibusData(minibusRoutes, roundedMinibusStops)
        if (exportToFile) {
            execute("Writing minibus data \"$MINIBUS_DATA_EXPORT_PATH\"...") {
                writeToGZ(minibusData.toJson(), MINIBUS_DATA_EXPORT_PATH)
            }
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
                    println("- Request minibus info for $region $number failed")
                }, onResponse = {
                    val data = MinibusRouteInfoResponse.fromJson(it)?.data
                    if (!data.isNullOrEmpty()) {
                        val routeInfo = data[0]
                        routeInfo.directions.forEach { direction ->
                            val routeStopResponse =
                                get("$MINIBUS_ROUTE_STOP_URL/${routeInfo.routeID}/${direction.routeSeq}")
                            val stops = MinibusRouteStopResponse.fromJson(routeStopResponse)?.data?.routeStops
                            val bound = if (direction.routeSeq == 1) Bound.O else Bound.I  // Can only be 1 or 2

                            val newRoute = MiniBusRoute(routeId = "${routeInfo.routeID}",
                                region = region,
                                number = number,
                                bound = bound,
                                originEn = direction.origEn,
                                originChiT = direction.origTc,
                                originChiS = ZhConverterUtil.toSimple(direction.origTc),
                                destEn = direction.destEn,
                                destChiT = direction.destTc,
                                destChiS = ZhConverterUtil.toSimple(direction.destTc),
                                fullFare = null,
                                stops = if (stops.isNullOrEmpty()) listOf() else stops.map { stop -> "${stop.stopID}" })

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
        println("- Minibus routes added (inbound & outbound): ${list.size}")
        return list
    }

    private fun getStops(stopIDsNeededInfo: Set<String>): List<MinibusStop> {
        this.stopIDs.clear()
        this.stopIDs.addAll(stopIDsNeededInfo)
        val list = mutableListOf<MinibusStop>()

        do {
            list.addAll(getStopsAsync())
            val size = this.stopIDs.size
            if (size != 0) {
                println("$size errors received, waiting for ${TIMEOUT_SECOND}s before restarting...")
                Thread.sleep(TIMEOUT_SECOND * 1000)
                println("Restarting...")
            }
        } while (this.stopIDs.isNotEmpty())
        return list
    }

    private fun getStopsAsync(): List<MinibusStop> {
        val list = mutableListOf<MinibusStop>()
        val totalCount = stopIDs.size
        val countDownLatch = CountDownLatch(totalCount)
        val start = System.currentTimeMillis()

        stopIDs.forEach { stopId ->
            getAsync("$MINIBUS_STOP_URL/$stopId", onFailure = {
                countDownLatch.countDown()
                // println("Request for minibus stopID $stopId failed")
            }, onResponse = { minibusStopResponse ->
                val data = MinibusStopResponse.fromJson(minibusStopResponse)?.data
                val lat = data?.coordinates?.wgs84?.latitude!!.roundCoordinate()
                val long = data.coordinates.wgs84.longitude.roundCoordinate()

                val response = get("$MINIBUS_STOP_ROUTE_URL/$stopId")
                var newStop: MinibusStop? = null
                try {
                    val stopRouteData = MinibusStopRouteResponse.fromJson(response)?.data
                    if (!stopRouteData.isNullOrEmpty()) {
                        // Choose the name set with the shortest standardize traditional Chinese name
                        val dataSetChosen = stopRouteData.minByOrNull { it.nameTc.standardizeChiStopName().length }!!
                        val nameTc = dataSetChosen.nameTc.standardizeChiStopName()

                        // Always generate simplified Chinese (Server fills it with traditional or is missing)
                        val nameSc = ZhConverterUtil.toSimple(nameTc)
                        newStop = MinibusStop(stopId, dataSetChosen.nameEn, nameTc, nameSc, listOf(lat, long))
                    }
                } catch (_: KlaxonException) {
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
    val minibusData = MinibusHelper().getMinibusData(exportToFile = true, exportIntermediates = true)
}