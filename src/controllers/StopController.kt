package controllers

import APIs
import APIs.Companion.CTB_ALL_STOP
import APIs.Companion.KMB_ALL_STOPS
import Company
import HttpHelper.Companion.get
import HttpHelper.Companion.getAsync
import Utils
import data.Stop
import json_models.CtbStopResponse
import json_models.KmbStopResponse
import json_models.NlbRouteStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sharedData
import java.util.concurrent.CountDownLatch

class StopController {
    companion object {
        private var stopsAdded = 0
        private lateinit var countDownLatch: CountDownLatch
        private const val CtbStopMaxId = 3827
        private const val CtbStopMinId = 1001
        private const val totalCtbStops = CtbStopMaxId - CtbStopMinId + 1
        private val mutex = Mutex()
        // todo get the stop number range from online source 3827

        fun getKmbStops(): Int {
            var stopsAdded = 0
            try {
                val response = get(KMB_ALL_STOPS)
                val kmbStops = KmbStopResponse.fromJson(response)?.data
                if (!kmbStops.isNullOrEmpty()) {
                    val newStops = kmbStops.map { x ->
                        Stop(Company.KMB, x.stop, x.nameEn, x.nameTc, x.nameSc, x.lat.toDouble(), x.long.toDouble())
                    }
                    sharedData.stops.addAll(newStops)
                    stopsAdded = newStops.size
                }
            } catch (e: Exception) {
                println("Error occurred while getting KMB stops \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return stopsAdded
        }

        fun getCtbStops(): Int {
            countDownLatch = CountDownLatch(totalCtbStops)
            val original = sharedData.stops.size
            for (i in CtbStopMinId..CtbStopMaxId) {
                getCtbStop(i)
            }
            countDownLatch.await()
            return sharedData.stops.size - original
        }

        private fun getCtbStop(id: Int) {
            val start = System.currentTimeMillis()
            try {
                val url = "$CTB_ALL_STOP/${String.format("%06d", id)}"
                getAsync(url, onFailure = {
                    println("Request failed: $url")
                    countDownLatch.countDown()
                    printGetCtbStopsProgress(1, start)
                }, onResponse = fun(responseBody) {
                    val ctbStop = CtbStopResponse.fromJson(responseBody)?.data
                    if (ctbStop?.stop != null) {
                        val newStop = Stop(
                            Company.CTB,
                            ctbStop.stop,
                            ctbStop.nameEn!!,
                            ctbStop.nameTc!!,
                            ctbStop.nameSc!!,
                            ctbStop.lat!!.toDouble(),
                            ctbStop.long!!.toDouble()
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            mutex.withLock { sharedData.stops.add(newStop) }
                        }
                    }
                    countDownLatch.countDown()
                    printGetCtbStopsProgress(50, start)
                })
            } catch (e: Exception) {
                println("Error occurred while getting CTB stop \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
        }

        private fun printGetCtbStopsProgress(intervalCount: Int, startTimeInMillis: Long) {
            val finishedCount = totalCtbStops - countDownLatch.count.toInt()
            if ((finishedCount % intervalCount) == 0) {
                Utils.printPercentage(finishedCount, totalCtbStops, startTimeInMillis)
            }
        }

        fun getNlbStops(): Int {
            val nlbRoutes = sharedData.requestableRoutes.filter { x -> x.company == Company.NLB }
            stopsAdded = 0
            countDownLatch = CountDownLatch(nlbRoutes.size)
            nlbRoutes.forEach { getNlbRouteStop(it.routeId!!) }
            countDownLatch.await()
            return stopsAdded
        }

        private fun getNlbRouteStop(routeId: String) {
            val url = "${APIs.NLB_ROUTE_STOP}$routeId"
            getAsync(url, fun(_) {
                println("Request failed: $url")
                countDownLatch.countDown()
            }, fun(responseBody) {
                val nlbRouteStopResponse = NlbRouteStopResponse.fromJson(responseBody)
                val nlbStop = nlbRouteStopResponse?.stops
                if (!nlbStop.isNullOrEmpty()) {
                    nlbStop.forEach {
                        CoroutineScope(Dispatchers.IO).launch {
                            mutex.withLock {
                                if (!sharedData.stops.any { x -> (x.stopId == it.stop) }) {
                                    sharedData.stops.add(
                                        Stop(
                                            Company.NLB,
                                            it.stop,
                                            it.stopNameE,
                                            it.stopNameC,
                                            it.stopNameS,
                                            it.latitude.toDouble(),
                                            it.longitude.toDouble()
                                        )
                                    )
                                    stopsAdded++
                                    // println("NLB Stop added: ${it.stopId} ($stopsAdded)")
                                }
                            }
                        }
                    }
                }
                countDownLatch.countDown()
            })
        }
    }
}