package helpers

import data.RemoteBusRoute
import json_models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import utils.APIs.Companion.CTB_ALL_ROUTES
import utils.APIs.Companion.CTB_ROUTE_STOP
import utils.APIs.Companion.KMB_ALL_ROUTES
import utils.APIs.Companion.KMB_ROUTE_STOP
import utils.APIs.Companion.NLB_ALL_ROUTES
import utils.APIs.Companion.NLB_ROUTE_STOP
import utils.Bound
import utils.Company
import utils.HttpUtils.Companion.get
import utils.HttpUtils.Companion.getAsync
import utils.Utils
import java.util.concurrent.CountDownLatch

class BusHelper {
    companion object {
        private val mutex = Mutex()
        fun getRoutes(company: Company): List<RemoteBusRoute> {
            val remoteBusRoutes = mutableListOf<RemoteBusRoute>()
            try {
                val url = when (company) {
                    Company.KMB, Company.LWB -> KMB_ALL_ROUTES
                    Company.CTB -> CTB_ALL_ROUTES
                    Company.NLB -> NLB_ALL_ROUTES
                    Company.MTRB -> TODO()
                }
                val response = get(url)

                when (company) {
                    Company.KMB, Company.LWB -> {
                        val kmbRoutes = KmbRouteResponse.fromJson(response)?.data
                        if (!kmbRoutes.isNullOrEmpty()) {
                            val kmbRemoteBusRoutes = mutableListOf<RemoteBusRoute>()
                            val countDownLatch = CountDownLatch(kmbRoutes.size)
                            kmbRoutes.forEach {
                                getRouteStopsAsync(
                                    Company.KMB,
                                    it.route,
                                    it.bound,
                                    it.serviceType.toInt(),
                                    onFailure = {
                                        countDownLatch.countDown()
                                    },
                                    onResponse = { stops ->
                                        val newRoute = RemoteBusRoute(
                                            Company.KMB,
                                            it.route,
                                            it.bound,
                                            it.origEn,
                                            it.origTc,
                                            it.origSc,
                                            it.destEn,
                                            it.destTc,
                                            it.destSc,
                                            it.serviceType.toInt(),
                                            null,
                                            stops
                                        )
                                        CoroutineScope(Dispatchers.IO).launch {
                                            mutex.withLock {
                                                kmbRemoteBusRoutes.add(newRoute)
                                                countDownLatch.countDown()
                                            }
                                        }
                                    })
                            }
                            countDownLatch.await()
                            kmbRemoteBusRoutes.sortWith(
                                compareBy({ it.number.toInt(Character.MAX_RADIX) },
                                    { it.bound },
                                    { it.kmbServiceType })
                            )
                            remoteBusRoutes.addAll(kmbRemoteBusRoutes)
                        }
                    }

                    Company.CTB -> {
                        val ctbRoutes = CtbRouteResponse.fromJson(response)?.data
                        if (!ctbRoutes.isNullOrEmpty()) {
                            val totalCount = ctbRoutes.size * 2
                            val countDownLatch = CountDownLatch(totalCount)
                            val ctbRemoteBusRoutes = mutableListOf<RemoteBusRoute>()
                            val start = System.currentTimeMillis()

                            ctbRoutes.forEach {
                                // Get routes for both bounds
                                Bound.entries.forEach { bound ->
                                    getRouteStopsAsync(Company.CTB, it.route, bound, null, onFailure = { _ ->
                                        countDownLatch.countDown()
                                        println("Request for CTB ${it.route} failed")
                                    }, onResponse = { stops ->
                                        if (stops.isNotEmpty()) {
                                            val newRoute = RemoteBusRoute(
                                                Company.CTB,
                                                it.route,
                                                bound,
                                                if (bound == Bound.O) it.origEn else it.destEn,
                                                if (bound == Bound.O) it.origTc else it.destTc,
                                                if (bound == Bound.O) it.origSc else it.destSc,
                                                if (bound == Bound.O) it.destEn else it.origEn,
                                                if (bound == Bound.O) it.destTc else it.origTc,
                                                if (bound == Bound.O) it.destSc else it.origSc,
                                                null,
                                                null,
                                                stops
                                            )
                                            CoroutineScope(Dispatchers.IO).launch {
                                                mutex.withLock {
                                                    ctbRemoteBusRoutes.add(newRoute)
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
                                    })
                                }
                            }
                            countDownLatch.await()
                            ctbRemoteBusRoutes.sortWith(
                                compareBy({ it.number.toInt(Character.MAX_RADIX) }, { it.bound })
                            )
                            remoteBusRoutes.addAll(ctbRemoteBusRoutes)
                        }
                    }

                    Company.NLB -> {
                        val nlbRoutes = NlbRouteResponse.fromJson(response)?.routes?.toMutableList()
                            ?.sortedBy { it.routeId.toInt() }
                        if (!nlbRoutes.isNullOrEmpty()) {
                            val nlbRemoteBusRoutes = mutableListOf<RemoteBusRoute>()
                            nlbRoutes.forEach {
                                val result = nlbRemoteBusRoutes.find { route -> route.number == it.routeNo }
                                val originEn = it.routeNameE.split('>')[0].trim()
                                val destEn = it.routeNameE.split('>')[1].trim()
                                val bound: Bound = if (result == null) {
                                    Bound.O
                                } else {
                                    if (result.originEn == originEn || result.destEn == destEn) {
                                        Bound.O
                                    } else {
                                        Bound.I
                                    }
                                }
                                val stops = getRouteStops(Company.NLB, it.routeId, null, null)
                                nlbRemoteBusRoutes.add(
                                    RemoteBusRoute(
                                        Company.NLB,
                                        it.routeNo,
                                        bound,
                                        originEn,
                                        it.routeNameC.split('>')[0].trim(),
                                        it.routeNameS.split('>')[0].trim(),
                                        destEn,
                                        it.routeNameC.split('>')[1].trim(),
                                        it.routeNameS.split('>')[1].trim(),
                                        null,
                                        it.routeId,
                                        stops
                                    )
                                )
                            }
                            remoteBusRoutes.addAll(nlbRemoteBusRoutes)
                        }
                    }

                    Company.MTRB -> TODO()
                }
            } catch (e: Exception) {
                println("Error occurred while getting ${company.name.uppercase()} routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return remoteBusRoutes
        }

        private fun getRouteStops(
            company: Company,
            number: String,
            bound: Bound?,
            serviceType: Int?,
        ): List<String> {
            val direction = if (bound == Bound.I) "inbound" else "outbound"
            val url = when (company) {
                Company.KMB, Company.LWB -> "$KMB_ROUTE_STOP/$number/$direction/$serviceType"
                Company.CTB -> "$CTB_ROUTE_STOP/$number/$direction"
                Company.NLB -> "$NLB_ROUTE_STOP$number"
                Company.MTRB -> TODO()
            }
            val response = get(url)
            val stops = mutableListOf<String>()
            when (company) {
                Company.KMB, Company.LWB -> {
                    val routeStops = KmbRouteStopResponse.fromJson(response)?.stops
                    if (!routeStops.isNullOrEmpty()) {
                        stops.addAll(routeStops.map { x -> x.stop })
                    }
                }

                Company.CTB -> {
                    val routeStops = CtbRouteStopResponse.fromJson(response)?.stops
                    if (!routeStops.isNullOrEmpty()) {
                        stops.addAll(routeStops.map { x -> x.stop })
                    }
                }

                Company.NLB -> {
                    NlbRouteStopResponse.fromJson(response)?.stops
                    val routeStops = NlbRouteStopResponse.fromJson(response)?.stops
                    if (!routeStops.isNullOrEmpty()) {
                        stops.addAll(routeStops.map { x -> x.stop })
                    }
                }

                Company.MTRB -> TODO()
            }
            return stops
        }

        private fun getRouteStopsAsync(
            company: Company,
            number: String,
            bound: Bound?,
            serviceType: Int?,
            onFailure: (call: Call) -> Unit,
            onResponse: (List<String>) -> Unit
        ) {
            val direction = if (bound == Bound.I) "inbound" else "outbound"
            val url = when (company) {
                Company.KMB, Company.LWB -> "$KMB_ROUTE_STOP/$number/$direction/$serviceType"
                Company.CTB -> "$CTB_ROUTE_STOP/$number/$direction"
                Company.NLB -> "$NLB_ROUTE_STOP/$number"
                Company.MTRB -> TODO()
            }
            getAsync(url = url, onFailure = onFailure, onResponse = { response ->
                when (company) {
                    Company.KMB, Company.LWB -> {
                        val routeStops = KmbRouteStopResponse.fromJson(response)?.stops
                        if (!routeStops.isNullOrEmpty()) {
                            onResponse(routeStops.map { x -> x.stop })
                        } else {
                            onResponse(listOf())
                        }
                    }

                    Company.CTB -> {
                        val routeStops = CtbRouteStopResponse.fromJson(response)?.stops
                        if (!routeStops.isNullOrEmpty()) {
                            onResponse(routeStops.map { x -> x.stop })
                        } else {
                            onResponse(listOf())
                        }
                    }

                    Company.NLB -> {
                        NlbRouteStopResponse.fromJson(response)?.stops
                        val routeStops = NlbRouteStopResponse.fromJson(response)?.stops
                        if (!routeStops.isNullOrEmpty()) {
                            onResponse(routeStops.map { x -> x.stop })
                        } else {
                            onResponse(listOf())
                        }
                    }

                    Company.MTRB -> TODO()
                }
            })
        }
    }
}