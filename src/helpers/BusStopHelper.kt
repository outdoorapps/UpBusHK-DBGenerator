package helpers

import data.RemoteBusRoute
import data.RemoteBusData
import data.BusStop
import json_models.CtbStopResponse
import json_models.KmbStopResponse
import json_models.NlbRouteStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utils.APIs
import utils.APIs.Companion.CTB_ALL_STOP
import utils.APIs.Companion.KMB_ALL_STOPS
import utils.Company
import utils.HttpUtils.Companion.get
import utils.HttpUtils.Companion.getAsync
import utils.Utils
import java.util.concurrent.CountDownLatch

class BusStopHelper {
    companion object {
        private val mutex = Mutex()

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

        fun getCtbStops(remoteBusRoutes: List<RemoteBusRoute>): List<BusStop> {
            val ctbRemoteRoutes = remoteBusRoutes.filter { it.company == Company.CTB }
            val ctbStopIDs = mutableListOf<String>()
            val ctbStops = mutableListOf<BusStop>()

            ctbRemoteRoutes.forEach {
                it.stops.forEach { stop -> if (!ctbStopIDs.contains(stop)) ctbStopIDs.add(stop) }
            }
            val totalCount = ctbStopIDs.size
            val countDownLatch = CountDownLatch(totalCount)

            val start = System.currentTimeMillis()
            ctbStopIDs.forEach {
                try {
                    val url = "$CTB_ALL_STOP/${String.format("%06d", it.toInt())}"
                    getAsync(url, onFailure = {
                        println("Request failed: $url")
                        countDownLatch.countDown()
                    }, onResponse = fun(responseBody) {
                        val ctbStop = CtbStopResponse.fromJson(responseBody)?.data
                        if (ctbStop?.stop != null) {
                            val newStop = BusStop(
                                Company.CTB,
                                ctbStop.stop,
                                ctbStop.nameEn!!,
                                ctbStop.nameTc!!,
                                ctbStop.nameSc!!,
                                mutableListOf(ctbStop.lat!!.toDouble(), ctbStop.long!!.toDouble())
                            )
                            CoroutineScope(Dispatchers.IO).launch {
                                mutex.withLock {
                                    ctbStops.add(newStop)
                                    countDownLatch.countDown()
                                }
                            }
                        } else {
                            countDownLatch.countDown()
                        }
                        val finishCount = totalCount - countDownLatch.count.toInt()
                        if (finishCount % 50 == 0) {
                            Utils.printPercentage(finishCount, totalCount, start)
                        }
                    })
                } catch (e: Exception) {
                    println(
                        "Error occurred while getting CTB stop \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString()
                    )
                }
            }
            countDownLatch.await()
            ctbStops.sortBy { it.stopId }
            return ctbStops
        }

        fun getNlbStops(remoteBusRoutes: List<RemoteBusRoute>): List<BusStop> {
            val nlbRoutes = remoteBusRoutes.filter { x -> x.company == Company.NLB }
            val nlbStops = mutableListOf<BusStop>()

            val countDownLatch = CountDownLatch(nlbRoutes.size)
            nlbRoutes.forEach { remoteRoute ->
                val url = "${APIs.NLB_ROUTE_STOP}${remoteRoute.routeId}"
                getAsync(url, fun(_) {
                    println("Request failed: $url")
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

        fun validateStops(remoteBusData: RemoteBusData): List<String> {
            print("Validating stops...")
            val noMatchStops = mutableListOf<String>()
            remoteBusData.remoteBusRoutes.forEach {
                it.stops.forEach { stop ->
                    if (!remoteBusData.busStops.any { remoteStop -> remoteStop.stopId == stop }) {
                        if (!noMatchStops.contains(stop)) noMatchStops.add(stop)
                    }
                }
            }
            if (noMatchStops.size > 0) println("Stops in bus routes but not found in the database: $noMatchStops")
            else println("Success")
            return noMatchStops
        }
    }
}