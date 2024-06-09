package helper

import com.beust.klaxon.Klaxon
import data.CompanyBusRoute
import data.GovBusData
import data.GovBusRoute
import json_model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.RequestBody.Companion.toRequestBody
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
import util.HttpUtils.Companion.jsonMediaType
import util.HttpUtils.Companion.post
import util.Paths.Companion.MTRB_SCHEDULE_URL
import util.Utils
import java.util.concurrent.CountDownLatch

class BusRouteHelper {
    companion object {
        private val mtrbRouteRegex = "K[0-9]+[A-Z]?|506".toRegex()
        private val mutex = Mutex()
        fun getRoutes(company: Company): List<CompanyBusRoute> {
            val companyBusRoutes = mutableListOf<CompanyBusRoute>()
            try {
                val url = when (company) {
                    Company.KMB, Company.LWB -> KMB_ALL_ROUTES
                    Company.CTB -> CTB_ALL_ROUTES
                    Company.NLB -> NLB_ALL_ROUTES
                    Company.MTRB -> return emptyList()
                }
                val response = get(url)

                when (company) {
                    Company.KMB, Company.LWB -> {
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
                                                kmbCompanyBusRoutes.add(newRoute)
                                                countDownLatch.countDown()
                                            }
                                        }
                                    })
                            }
                            countDownLatch.await()
                            kmbCompanyBusRoutes.sortWith(compareBy({ it.number.toInt(Character.MAX_RADIX) },
                                { it.bound },
                                { it.kmbServiceType })
                            )
                            companyBusRoutes.addAll(kmbCompanyBusRoutes)
                        }
                    }

                    Company.CTB -> {
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
                            companyBusRoutes.addAll(nlbCompanyBusRoutes)
                        }
                    }

                    else -> return emptyList()
                }
            } catch (e: Exception) {
                println("Error occurred while getting ${company.name.uppercase()} routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return companyBusRoutes
        }

        fun getMtrbRoutes(govBusData: GovBusData): List<GovBusRoute> {
            val mtrbRoutes = govBusData.govBusRoutes.filter { e -> e.routeNameE.matches(mtrbRouteRegex) }
            val mtrbRoutesValidated = mutableListOf<GovBusRoute>()
            mtrbRoutes.forEach {
                val response = post(
                    MTRB_SCHEDULE_URL, MtrbRequestBody("en", it.routeNameE).toJson().toRequestBody(
                        jsonMediaType
                    )
                )
                val mtrbScheduleResponse = Klaxon().parse<MtrbScheduleResponse>(response)
                if (mtrbScheduleResponse?.footerRemarks != null) {
                    mtrbRoutesValidated.add(it)
                }
            }
            // todo K53,K73,K75P has duplicates multiple types?
            mtrbRoutesValidated.sortBy { it.routeNameE }
            return mtrbRoutesValidated
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