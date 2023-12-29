package controllers

import APIs
import APIs.Companion.CTB_ALL_STOP
import APIs.Companion.KMB_ALL_STOPS
import Company
import HttpHelper.Companion.get
import HttpHelper.Companion.getAsync
import SharedData.Companion.mutex
import data.Stop
import json_models.CtbStopResponse
import json_models.KmbStopResponse
import json_models.NlbRouteStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import sharedData
import java.util.concurrent.CountDownLatch

class StopController {
    companion object {
        private var stopsAdded = 0
        private lateinit var countDownLatch: CountDownLatch
        private const val CtbStopMaxId = 3822
        private const val CtbStopMinId = 1001
        private const val totalCtbStops = CtbStopMaxId - CtbStopMinId + 1
        // todo get the stop number range from online source 3823

        fun getKmbStops(): Int {
            var stopsAdded = 0
            try {
                val response = get(KMB_ALL_STOPS)
                val kmbStops = KmbStopResponse.fromJson(response)?.data
                if (!kmbStops.isNullOrEmpty()) {
                    val newStops = kmbStops.map { x ->
                        Stop(Company.kmb, x.stop, x.nameEn, x.nameTc, x.nameSc, x.lat.toDouble(), x.long.toDouble())
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
            try {
                val url = "$CTB_ALL_STOP/${String.format("%06d", id)}"
                getAsync(url, fun(_) {
                    println("Request failed: $url")
                    countDownLatch.countDown()
                    printGetCtbStopsProgress(1)
                }, fun(responseBody) {
                    val ctbStop = CtbStopResponse.fromJson(responseBody)?.data
                    if (ctbStop?.stop != null) {
                        val newStop = Stop(
                            Company.ctb,
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
                    printGetCtbStopsProgress(50)
                })
            } catch (e: Exception) {
                println("Error occurred while getting CTB stop \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
        }

        private fun printGetCtbStopsProgress(intervalCount: Int) {
            val finished = totalCtbStops - countDownLatch.count
            if ((finished % intervalCount).toInt() == 0) {
                val percentage = finished.toDouble() / totalCtbStops.toDouble() * 100
                println("($finished/$totalCtbStops) ${String.format("%.1f", percentage)} % done")
            }
        }

        fun getNlbStops(): Int {
            val nlbRoutes = sharedData.routes.filter { x -> x.company == Company.nlb }
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
                                if (!sharedData.stops.any { x -> (x.stopId == it.stopId) }) {
                                    sharedData.stops.add(
                                        Stop(
                                            Company.nlb,
                                            it.stopId,
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