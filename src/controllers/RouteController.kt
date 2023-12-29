package controllers

import APIs.Companion.CTB_ALL_ROUTES
import APIs.Companion.KMB_ALL_ROUTES
import APIs.Companion.NLB_ALL_ROUTES
import Bound
import Company
import HttpHelper.Companion.get
import data.Route
import json_models.CtbRouteResponse
import json_models.KmbRouteResponse
import json_models.NlbRouteResponse
import sharedData

class RouteController {
    companion object {
        private var routesAdded = 0

        fun getRoutes(company: Company): Int {
            try {
                val url = when (company) {
                    Company.kmb -> KMB_ALL_ROUTES
                    Company.ctb -> CTB_ALL_ROUTES
                    Company.nlb -> NLB_ALL_ROUTES
                    Company.mtrb -> "" //todo
                }
                val response = get(url)

                when (company) {
                    Company.kmb -> {
                        val kmbRoutes = KmbRouteResponse.fromJson(response)?.data
                        if (!kmbRoutes.isNullOrEmpty()) {
                            val newRoutes = kmbRoutes.map { x ->
                                Route(
                                    Company.kmb,
                                    x.route,
                                    x.bound,
                                    null,
                                    x.origEn,
                                    x.origTc,
                                    x.origSc,
                                    x.destEn,
                                    x.destTc,
                                    x.destSc,
                                    x.serviceType.toInt()
                                )
                            }
                            sharedData.routes.addAll(newRoutes)
                            routesAdded = newRoutes.size
                        }
                    }

                    Company.ctb -> {
                        val ctbRoutes = CtbRouteResponse.fromJson(response)?.data
                        if (!ctbRoutes.isNullOrEmpty()) {
                            val newRoutes = ctbRoutes.map { x ->
                                Route(
                                    Company.ctb,
                                    x.route,
                                    Bound.O,
                                    null,
                                    x.origEn,
                                    x.origTc,
                                    x.origSc,
                                    x.destEn,
                                    x.destTc,
                                    x.destSc,
                                    null,
                                )
                            }
                            val generatedRoutes = ctbRoutes.map { x ->
                                Route(
                                    Company.ctb,
                                    x.route,
                                    Bound.I,
                                    null,
                                    x.destEn,
                                    x.destTc,
                                    x.destSc,
                                    x.origEn,
                                    x.origTc,
                                    x.origSc,
                                    null
                                )
                            }
                            sharedData.routes.addAll(newRoutes)
                            sharedData.routes.addAll(generatedRoutes)
                            routesAdded = newRoutes.size
                        }
                    }

                    Company.nlb -> {
                        val nlbRoutes = NlbRouteResponse.fromJson(response)?.routes?.toMutableList()
                            ?.apply { sortBy { it.routeId.toInt() } }
                        val newRoutes = mutableListOf<Route>()
                        if (!nlbRoutes.isNullOrEmpty()) {
                            nlbRoutes.forEach {
                                val result = newRoutes.find { route -> route.number == it.routeNo }
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
                                newRoutes.add(
                                    Route(
                                        Company.nlb,
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
                                    )
                                )
                            }
                            sharedData.routes.addAll(newRoutes)
                            routesAdded = newRoutes.size
                        }
                    }

                    Company.mtrb -> TODO()
                }
            } catch (e: Exception) {
                println("Error occurred while getting ${company.name.uppercase()} routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
            }
            return routesAdded
        }
    }
}