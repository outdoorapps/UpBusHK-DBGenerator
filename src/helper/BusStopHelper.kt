package helper

import data.BusStop
import data.CompanyBusData
import data.CompanyBusRoute
import json_model.CtbStopResponse
import json_model.KmbStopResponse
import json_model.NlbRouteStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import util.APIs
import util.APIs.Companion.CTB_ALL_STOP
import util.APIs.Companion.KMB_ALL_STOPS
import util.Company
import util.HttpUtils.Companion.get
import util.HttpUtils.Companion.getAsync
import util.Patch.Companion.accountedStops
import util.Utils
import java.util.concurrent.CountDownLatch

class BusStopHelper {
    companion object {
        private const val TIMEOUT_SECOND = 120L
    }

    private val mutex = Mutex()
    private val ctbStopIDs = mutableSetOf<String>()

    fun getKmbStops(): List<BusStop> {
        val busStops = mutableListOf<BusStop>()
        try {
            val response = get(KMB_ALL_STOPS)
            val kmbStops = KmbStopResponse.fromJson(response)?.data
            if (!kmbStops.isNullOrEmpty()) {
                val newStops = kmbStops.map { x ->
                    BusStop(
                        Company.KMB,
                        x.stop,
                        x.nameEn,
                        x.nameTc,
                        x.nameSc,
                        mutableListOf(x.lat.toDouble(), x.long.toDouble())
                    )
                }
                busStops.addAll(newStops.sortedBy { it.stopId })
            }
        } catch (e: Exception) {
            println("Error occurred while getting KMB stops \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
        }
        return busStops
    }

    fun getCtbStops(companyBusRoutes: List<CompanyBusRoute>): List<BusStop> {
        ctbStopIDs.clear()
        val ctbStops = mutableListOf<BusStop>()
        val ctbBusCompanyRoutes = companyBusRoutes.filter { it.company == Company.CTB }
        ctbBusCompanyRoutes.forEach { ctbStopIDs.addAll(it.stops) }

        do {
            ctbStops.addAll(getCtbStopsAsync())
            val size = ctbStopIDs.size
            if (size != 0) {
                println("$size errors received for CTB stop IDs $ctbStopIDs, waiting for ${TIMEOUT_SECOND}s before restarting...")
                Thread.sleep(TIMEOUT_SECOND * 1000)
                println("Restarting...")
            }
        } while (ctbStopIDs.isNotEmpty())
        ctbStops.sortBy { it.stopId }
        return ctbStops
    }

    private fun getCtbStopsAsync(): List<BusStop> {
        val list = mutableListOf<BusStop>()

        val totalCount = ctbStopIDs.size
        val countDownLatch = CountDownLatch(totalCount)

        val start = System.currentTimeMillis()
        ctbStopIDs.forEach { stopId ->
            try {
                val url = "$CTB_ALL_STOP/${String.format("%06d", stopId.toInt())}"
                getAsync(url, onFailure = {
                    println("Request failed for CTB stop: $stopId")
                    countDownLatch.countDown()
                }, onResponse = fun(responseBody) {
                    val ctbStop = CtbStopResponse.fromJson(responseBody)?.data
                    var newStop: BusStop? = null
                    if (ctbStop?.stop != null) {
                        newStop = BusStop(
                            Company.CTB,
                            ctbStop.stop,
                            ctbStop.nameEn!!,
                            ctbStop.nameTc!!,
                            ctbStop.nameSc!!,
                            mutableListOf(ctbStop.lat!!.toDouble(), ctbStop.long!!.toDouble())
                        )
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        mutex.withLock {
                            if (newStop != null) {
                                list.add(newStop)
                                ctbStopIDs.remove(stopId)
                            }
                            countDownLatch.countDown()
                            val finishCount = totalCount - countDownLatch.count.toInt()
                            if (finishCount % 50 == 0) {
                                Utils.printPercentage(finishCount, totalCount, start)
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                println(
                    "Error occurred while getting CTB stop \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString()
                )
            }
        }
        countDownLatch.await()
        list.sortBy { it.stopId }
        return list
    }

    fun getNlbStops(companyBusRoutes: List<CompanyBusRoute>): List<BusStop> {
        val nlbRoutes = companyBusRoutes.filter { x -> x.company == Company.NLB }
        val nlbStops = mutableListOf<BusStop>()

        val countDownLatch = CountDownLatch(nlbRoutes.size)
        nlbRoutes.forEach { busCompanyRoute ->
            val url = "${APIs.NLB_ROUTE_STOP}${busCompanyRoute.nlbRouteId}"
            getAsync(url, fun(_) {
                println("Request failed for NLB stops on route: ${busCompanyRoute.nlbRouteId}")
                countDownLatch.countDown()
            }, fun(responseBody) {
                val nlbRouteStopResponse = NlbRouteStopResponse.fromJson(responseBody)
                val nlbStop = nlbRouteStopResponse?.stops
                if (!nlbStop.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        mutex.withLock {
                            nlbStop.forEach {
                                if (!nlbStops.any { x -> (x.stopId == it.stop) }) {
                                    nlbStops.add(
                                        BusStop(
                                            Company.NLB,
                                            it.stop,
                                            it.stopNameE,
                                            it.stopNameC,
                                            it.stopNameS,
                                            mutableListOf(it.latitude.toDouble(), it.longitude.toDouble())
                                        )
                                    )
                                }
                            }
                            countDownLatch.countDown()
                        }
                    }
                } else {
                    countDownLatch.countDown()
                }
            })
        }
        countDownLatch.await()
        nlbStops.sortBy { it.stopId }
        return nlbStops
    }

    fun getMtrbStops(): List<BusStop> {
        val busStops = mutableListOf<BusStop>()
        val mtrbRouteMap = MtrbDataParser.parseMtrbData()
        mtrbRouteMap.forEach { (_, boundMap) ->
            boundMap.forEach { (_, stops) ->
                busStops.addAll(stops)
            }
        }
        return busStops
    }

    fun validateStops(companyBusData: CompanyBusData): Set<String> {
        print("Validating stops...")
        val missingStops = mutableSetOf<String>()
        // Find all stops that are in a route but missing in the database
        companyBusData.companyBusRoutes.forEach {
            it.stops.forEach { stop ->
                if (!companyBusData.busStops.any { busStop -> busStop.stopId == stop }) {
                    missingStops.add(stop)
                }
            }
        }

        val unaccountedStops = mutableListOf<String>()
        if (missingStops.isNotEmpty()) {
            missingStops.forEach { if (!accountedStops.contains(it)) unaccountedStops.add(it) }
        }
        if (unaccountedStops.isNotEmpty()) {
            println("Stops that are not in the database and are unaccounted for their absence: $unaccountedStops")
        } else {
            println("Success")
        }
        return missingStops
    }
}