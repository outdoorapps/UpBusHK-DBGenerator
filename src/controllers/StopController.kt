package controllers

import APIs
import APIs.Companion.CTB_ALL_STOP
import APIs.Companion.KMB_ALL_STOPS
import Company
import HttpHelper.Companion.get
import HttpHelper.Companion.getAsync
import Utils
import data.RequestableStop
import json_models.CtbStopResponse
import json_models.KmbStopResponse
import json_models.NlbRouteStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import requestables
import java.util.concurrent.CountDownLatch

class StopController {
    companion object {
        private val mutex = Mutex()

        fun getKmbStops(): Int {
            var stopsAdded = 0
            try {
                val response = get(KMB_ALL_STOPS)
                val kmbStops = KmbStopResponse.fromJson(response)?.data
                if (!kmbStops.isNullOrEmpty()) {
                    val newStops = kmbStops.map { x ->
                        RequestableStop(
                            Company.KMB,
                            x.stop,
                            x.nameEn,
                            x.nameTc,
                            x.nameSc,
                            mutableListOf(x.lat.toDouble(), x.long.toDouble())
                        )
                    }
                    requestables.requestableStops.addAll(newStops.sortedBy { it.stopId })
                    stopsAdded = newStops.size
                }
            } catch (e: Exception) {
                println("Error occurred while getting KMB stops \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return stopsAdded
        }

        fun getCtbStops(): Int {
            val ctbRequestableRoutes = requestables.requestableRoutes.filter { it.company == Company.CTB }
            val ctbStopIDs = mutableListOf<String>()
            val ctbStops = mutableListOf<RequestableStop>()

            ctbRequestableRoutes.forEach {
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
                            val newStop = RequestableStop(
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
            requestables.requestableStops.addAll(ctbStops)
            return ctbStops.size
        }

        fun getNlbStops(): Int {
            val nlbRoutes = requestables.requestableRoutes.filter { x -> x.company == Company.NLB }
            val nlbStops = mutableListOf<RequestableStop>()

            val countDownLatch = CountDownLatch(nlbRoutes.size)
            nlbRoutes.forEach { requestableRoute ->
                val url = "${APIs.NLB_ROUTE_STOP}${requestableRoute.routeId}"
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
                                            RequestableStop(
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
            requestables.requestableStops.addAll(nlbStops)
            return nlbStops.size
        }

        fun validateStops(): List<String> {
            print("Validating stops...")
            val noMatchStops = mutableListOf<String>()
            requestables.requestableRoutes.forEach {
                it.stops.forEach { stop ->
                    if (!requestables.requestableStops.any { requestableStop -> requestableStop.stopId == stop }) {
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