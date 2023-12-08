package controllers

import APIs.Companion.CTB_ALL_STOP
import APIs.Companion.KMB_ALL_STOPS
import HttpHelper.Companion.get
import HttpHelper.Companion.getAsync
import data.Stop
import json_models.CtbStopResponse
import json_models.KmbStopResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.CountDownLatch

class StopsController {
    companion object {
        private val mutex = Mutex()
        val stops: MutableList<Stop> = mutableListOf()
        private var ctbStopsAdded = 0
        private val CtbStopMaxId = 3823
        private val CtbStopMinId = 1001
        private val totalCtbStops = CtbStopMaxId - CtbStopMinId + 1;
        private val countDownLatch = CountDownLatch(totalCtbStops)
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
                    stops.addAll(newStops)
                    stopsAdded = newStops.size
                }
            } catch (e: Exception) {
                println("Error occurred while getting KMB stops \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return stopsAdded
        }

        fun getCtbStops(): Int {
            ctbStopsAdded = 0
            for (i in CtbStopMinId..CtbStopMaxId) {
                getCtbStop(i)
            }
            countDownLatch.await()
            return ctbStopsAdded //todo
        }

        private fun getCtbStop(id: Int) {
            try {
                val url = "$CTB_ALL_STOP/${String.format("%06d", id)}"
                getAsync(url, fun(_) {
                    println("Request failed: $url")
                    countDownLatch.countDown()
                    printGetCtbStopsProgress()
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
                            addStop(newStop)
                            countDownLatch.countDown()
                            printGetCtbStopsProgress()
                        }
                    }
                })
            } catch (e: Exception) {
                println("Error occurred while getting CTB stop \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
        }

        private fun printGetCtbStopsProgress() {
            val finished = totalCtbStops - countDownLatch.count
            // print every 50 requests finished
            if ((finished % 50).toInt() == 0) {
                val percentage = finished.toDouble() / totalCtbStops.toDouble() * 100
                println("($finished/$totalCtbStops) ${String.format("%.1f", percentage)} % done")
            }
        }

        fun getNlbStops() :Int {
           val nlbStops = stops.distinctBy { x-> x.company == Company.nlb }
            println(nlbStops.size)
            return 0
        }
        private suspend fun addStop(stop: Stop) {
            mutex.lock()
            try {
                stops.add(stop)
                ctbStopsAdded++
            } finally {
                mutex.unlock()
            }
        }
    }
}