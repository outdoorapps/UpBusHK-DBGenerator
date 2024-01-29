package controllers

import APIs.Companion.CTB_ALL_ROUTES
import APIs.Companion.CTB_ROUTE_STOP
import APIs.Companion.KMB_ALL_ROUTES
import APIs.Companion.KMB_ROUTE_STOP
import APIs.Companion.NLB_ALL_ROUTES
import APIs.Companion.NLB_ROUTE_STOP
import Bound
import Company
import HttpHelper.Companion.get
import HttpHelper.Companion.getAsync
import Utils
import data.RequestableRoute
import json_models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import requestables
import java.util.concurrent.CountDownLatch

class RouteController {
    companion object {
        private val mutex = Mutex()
        fun getRoutes(company: Company): Int {
            var routesAdded = 0
            try {
                val url = when (company) {
                    Company.KMB -> KMB_ALL_ROUTES
                    Company.CTB -> CTB_ALL_ROUTES
                    Company.NLB -> NLB_ALL_ROUTES
                    Company.MTRB -> TODO()
                }
                val response = get(url)

                when (company) {
                    Company.KMB -> {
                        val kmbRoutes = KmbRouteResponse.fromJson(response)?.data
                        if (!kmbRoutes.isNullOrEmpty()) {
                            val kmbRequestableRoutes = mutableListOf<RequestableRoute>()
                            val countDownLatch = CountDownLatch(kmbRoutes.size)
                            kmbRoutes.forEach {
                                getRouteStopsAsync(Company.KMB,
                                    it.route,
                                    it.bound,
                                    it.serviceType.toInt(),
                                    onFailure = {
                                        countDownLatch.countDown()
                                    },
                                    onResponse = { stops ->
                                        val newRoute = RequestableRoute(
                                            Company.KMB,
                                            it.route,
                                            it.bound,
                                            null,
                                            it.origEn,
                                            it.origTc,
                                            it.origSc,
                                            it.destEn,
                                            it.destTc,
                                            it.destSc,
                                            it.serviceType.toInt(),
                                            stops
                                        )
                                        CoroutineScope(Dispatchers.IO).launch {
                                            mutex.withLock {
                                                kmbRequestableRoutes.add(newRoute)
                                                countDownLatch.countDown()
                                            }
                                        }
                                    })
                            }
                            countDownLatch.await()
                            kmbRequestableRoutes.sortWith(
                                compareBy({ it.number.toInt(Character.MAX_RADIX) },
                                    { it.bound },
                                    { it.kmbServiceType })
                            )
                            requestables.requestableRoutes.addAll(kmbRequestableRoutes)
                            routesAdded = kmbRequestableRoutes.size
                        }
                    }

                    Company.CTB -> {
                        val ctbRoutes = CtbRouteResponse.fromJson(response)?.data
                        if (!ctbRoutes.isNullOrEmpty()) {
                            val totalCount = ctbRoutes.size * 2
                            val countDownLatch = CountDownLatch(totalCount)
                            val ctbRequestableRoutes = mutableListOf<RequestableRoute>()
                            val start = System.currentTimeMillis()

                            ctbRoutes.forEach {
                                // Get routes for both bounds
                                Bound.entries.forEach { bound ->
                                    getRouteStopsAsync(Company.CTB, it.route, bound, null, onFailure = { _ ->
                                        countDownLatch.countDown()
                                        println("Request for CTB ${it.route} failed")
                                    }, onResponse = { stops ->
                                        if (stops.isNotEmpty()) {
                                            val newRoute = RequestableRoute(
                                                Company.CTB,
                                                it.route,
                                                bound,
                                                null,
                                                if (bound == Bound.O) it.origEn else it.destEn,
                                                if (bound == Bound.O) it.origTc else it.destTc,
                                                if (bound == Bound.O) it.origSc else it.destSc,
                                                if (bound == Bound.O) it.destEn else it.origEn,
                                                if (bound == Bound.O) it.destTc else it.origTc,
                                                if (bound == Bound.O) it.destSc else it.origSc,
                                                null,
                                                stops
                                            )
                                            CoroutineScope(Dispatchers.IO).launch {
                                                mutex.withLock {
                                                    ctbRequestableRoutes.add(newRoute)
                                                    countDownLatch.countDown()
                                                }
                                            }
                                        } else {
                                            countDownLatch.countDown()
                                        }
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
                            ctbRequestableRoutes.sortWith(
                                compareBy({ it.number.toInt(Character.MAX_RADIX) }, { it.bound })
                            )
                            requestables.requestableRoutes.addAll(ctbRequestableRoutes)
                            routesAdded = ctbRequestableRoutes.size
                        }
                    }

                    Company.NLB -> {
                        val nlbRoutes = NlbRouteResponse.fromJson(response)?.routes?.toMutableList()
                            ?.sortedBy { it.routeId.toInt() }
                        if (!nlbRoutes.isNullOrEmpty()) {
                            val nlbRequestableRoutes = mutableListOf<RequestableRoute>()
                            nlbRoutes.forEach {
                                val result = nlbRequestableRoutes.find { route -> route.number == it.routeNo }
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
                                requestables.requestableRoutes.add(
                                    RequestableRoute(
                                        Company.NLB,
                                        it.routeNo,
                                        bound,
                                        it.routeId,
                                        originEn,
                                        it.routeNameC.split('>')[0].trim(),
                                        it.routeNameS.split('>')[0].trim(),
                                        destEn,
                                        it.routeNameC.split('>')[1].trim(),
                                        it.routeNameS.split('>')[1].trim(),
                                        null,
                                        stops
                                    )
                                )
                                routesAdded++
                            }
                        }
                    }

                    Company.MTRB -> TODO()
                }
            } catch (e: Exception) {
                println("Error occurred while getting ${company.name.uppercase()} routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return routesAdded
        }

        private fun getRouteStops(
            company: Company,
            number: String,
            bound: Bound?,
            serviceType: Int?,
        ): List<String> {
            val direction = if (bound == Bound.I) "inbound" else "outbound"
            val url = when (company) {
                Company.KMB -> "$KMB_ROUTE_STOP/$number/$direction/$serviceType"
                Company.CTB -> "$CTB_ROUTE_STOP/$number/$direction"
                Company.NLB -> "$NLB_ROUTE_STOP$number"
                Company.MTRB -> TODO()
            }
            val response = get(url)
            val stops = mutableListOf<String>()
            when (company) {
                Company.KMB -> {
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
                Company.KMB -> "$KMB_ROUTE_STOP/$number/$direction/$serviceType"
                Company.CTB -> "$CTB_ROUTE_STOP/$number/$direction"
                Company.NLB -> "$NLB_ROUTE_STOP/$number"
                Company.MTRB -> TODO()
            }
            getAsync(url = url, onFailure = onFailure, onResponse = { response ->
                when (company) {
                    Company.KMB -> {
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