package helper

import data.CompanyBusRoute
import json_model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import util.APIs.Companion.CTB_ALL_ROUTES
import util.APIs.Companion.CTB_ROUTE_STOP
import util.APIs.Companion.KMB_ALL_ROUTES
import util.APIs.Companion.KMB_ROUTE_STOP
import util.APIs.Companion.NLB_ALL_ROUTES
import util.APIs.Companion.NLB_ROUTE_STOP
import util.Bound
import util.Company
import util.HttpUtils.Companion.get
import util.HttpUtils.Companion.getAsync
import util.Utils
import java.util.concurrent.CountDownLatch

class BusRouteHelper {
    companion object {
        private val mutex = Mutex()
        fun getRoutes(company: Company): List<CompanyBusRoute> {
            val companyBusRoutes = mutableListOf<CompanyBusRoute>()
            try {
                when (company) {
                    Company.KMB, Company.LWB -> {
                        val response = get(KMB_ALL_ROUTES)
                        val kmbRoutes = KmbRouteResponse.fromJson(response)?.data
                        if (!kmbRoutes.isNullOrEmpty()) {
                            val kmbCompanyBusRoutes = mutableListOf<CompanyBusRoute>()
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
                                        val newRoute = CompanyBusRoute(
                                           company =  Company.KMB,
                                            number = it.route,
                                            bound = it.bound,
                                            originEn = it.origEn,
                                            originChiT = it.origTc,
                                            originChiS = it.origSc,
                                            destEn = it.destEn,
                                            destChiT = it.destTc,
                                            destChiS = it.destSc,
                                            serviceType = it.serviceType.toInt(),
                                            nlbRouteId = null,
                                            stops = stops
                                        )
                                        CoroutineScope(Dispatchers.IO).launch {
                                            mutex.withLock {
                                                kmbCompanyBusRoutes.add(newRoute)
                                                countDownLatch.countDown()
                                            }
                                        }
                                    })
                            }
                            countDownLatch.await()
                            kmbCompanyBusRoutes.sortWith(compareBy({ it.number.toInt(Character.MAX_RADIX) },
                                { it.bound },
                                { it.serviceType })
                            )
                            companyBusRoutes.addAll(kmbCompanyBusRoutes)
                        }
                    }

                    Company.CTB -> {
                        val response = get(CTB_ALL_ROUTES)
                        val ctbRoutes = CtbRouteResponse.fromJson(response)?.data
                        if (!ctbRoutes.isNullOrEmpty()) {
                            val totalCount = ctbRoutes.size * 2
                            val countDownLatch = CountDownLatch(totalCount)
                            val ctbCompanyBusRoutes = mutableListOf<CompanyBusRoute>()
                            val start = System.currentTimeMillis()

                            ctbRoutes.forEach {
                                // Get routes for both bounds
                                Bound.entries.forEach { bound ->
                                    getRouteStopsAsync(Company.CTB, it.route, bound, null, onFailure = { _ ->
                                        countDownLatch.countDown()
                                        println("Request for CTB ${it.route} failed")
                                    }, onResponse = { stops ->
                                        if (stops.isNotEmpty()) {
                                            val newRoute = CompanyBusRoute(
                                                company = Company.CTB,
                                                number = it.route,
                                                bound = bound,
                                                originEn = if (bound == Bound.O) it.origEn else it.destEn,
                                                originChiT = if (bound == Bound.O) it.origTc else it.destTc,
                                                originChiS = if (bound == Bound.O) it.origSc else it.destSc,
                                                destEn = if (bound == Bound.O) it.destEn else it.origEn,
                                                destChiT = if (bound == Bound.O) it.destTc else it.origTc,
                                                destChiS = if (bound == Bound.O) it.destSc else it.origSc,
                                                serviceType = null,
                                                nlbRouteId = null,
                                                stops = stops
                                            )
                                            CoroutineScope(Dispatchers.IO).launch {
                                                mutex.withLock {
                                                    ctbCompanyBusRoutes.add(newRoute)
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
                            ctbCompanyBusRoutes.sortWith(
                                compareBy({ it.number.toInt(Character.MAX_RADIX) }, { it.bound })
                            )
                            companyBusRoutes.addAll(ctbCompanyBusRoutes)
                        }
                    }

                    Company.NLB -> {
                        val response = get(NLB_ALL_ROUTES)
                        val nlbRoutes = NlbRouteResponse.fromJson(response)?.routes?.toMutableList()
                            ?.sortedBy { it.routeId.toInt() }
                        if (!nlbRoutes.isNullOrEmpty()) {
                            val nlbCompanyBusRoutes = mutableListOf<CompanyBusRoute>()
                            nlbRoutes.forEach {
                                val result = nlbCompanyBusRoutes.find { route -> route.number == it.routeNo }
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
                                val stops = getNlbRouteStops(it.routeId)
                                nlbCompanyBusRoutes.add(
                                    CompanyBusRoute(
                                        company = Company.NLB,
                                        number = it.routeNo,
                                        bound = bound,
                                        originEn = originEn,
                                        originChiT = it.routeNameC.split('>')[0].trim(),
                                        originChiS = it.routeNameS.split('>')[0].trim(),
                                        destEn = destEn,
                                        destChiT = it.routeNameC.split('>')[1].trim(),
                                        destChiS = it.routeNameS.split('>')[1].trim(),
                                        serviceType = null,
                                        nlbRouteId = it.routeId,
                                        stops = stops
                                    )
                                )
                            }
                            companyBusRoutes.addAll(nlbCompanyBusRoutes)
                        }
                    }

                    Company.MTRB -> {
                        val mtrbRouteMap = MtrbDataParser.parseMtrbData()
                        mtrbRouteMap.forEach { (routeName, boundMap) ->
                            boundMap.forEach { (bound, stops) ->
                                val origin = stops.first()
                                val dest = stops.last()
                                companyBusRoutes.add(
                                    CompanyBusRoute(company = Company.MTRB,
                                        number = routeName,
                                        bound = bound,
                                        originEn = origin.engName,
                                        originChiT = origin.chiTName,
                                        originChiS = origin.chiSName,
                                        destEn = dest.engName,
                                        destChiT = dest.chiTName,
                                        destChiS = dest.chiSName,
                                        serviceType = null,
                                        nlbRouteId = null,
                                        stops = stops.map { it.stopId })
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error occurred while getting ${company.name.uppercase()} routes \"${object {}.javaClass.enclosingMethod.name}\" : ${e.stackTraceToString()}")
            }
            return companyBusRoutes
        }

        private fun getNlbRouteStops(number: String): List<String> {
            val url = "$NLB_ROUTE_STOP$number"
            val response = get(url)
            val stops = mutableListOf<String>()
            NlbRouteStopResponse.fromJson(response)?.stops
            val routeStops = NlbRouteStopResponse.fromJson(response)?.stops
            if (!routeStops.isNullOrEmpty()) {
                stops.addAll(routeStops.map { x -> x.stop })
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
                Company.MTRB -> return
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

                    else -> {}
                }
            })
        }
    }
}