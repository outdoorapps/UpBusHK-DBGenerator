package controllers

import APIs.Companion.CTB_ALL_ROUTES
import APIs.Companion.KMB_ALL_ROUTES
import APIs.Companion.NLB_ALL_ROUTES
import Company
import HttpHelper.Companion.get
import data.Route
import json_models.CtbRouteResponse
import json_models.KmbRouteResponse
import json_models.NlbRouteResponse

class RouteController {
    companion object {
        val routes: MutableList<Route> = mutableListOf()
        var routesAdded = 0

        fun getRoutes(company: Company) : Int{
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
                                    company,
                                    x.route,
                                    x.bound,
                                    x.origEn,
                                    x.origTc,
                                    x.origSc,
                                    x.destEn,
                                    x.destTc,
                                    x.destSc
                                )
                            }
                            routes.addAll(newRoutes)
                            routesAdded = newRoutes.size
                        }
                    }

                    Company.ctb -> {
                        val ctbRoutes = CtbRouteResponse.fromJson(response)?.data
                        if (!ctbRoutes.isNullOrEmpty()) {
                            val newRoutes = ctbRoutes.map { x ->
                                Route(
                                    company,
                                    x.route,
                                    null,
                                    x.origEn,
                                    x.origTc,
                                    x.origSc,
                                    x.destEn,
                                    x.destTc,
                                    x.destSc
                                )
                            }
                            routes.addAll(newRoutes)
                            routesAdded = newRoutes.size
                        }
                    }

                    Company.nlb -> {
                        val nlbRoutes = NlbRouteResponse.fromJson(response)?.routes
                        if (!nlbRoutes.isNullOrEmpty()) {
                            val newRoutes = nlbRoutes.map { x ->
                                Route(
                                    company,
                                    x.routeNo,
                                    null,
                                    x.routeNameE.split('>')[0].trim(),
                                    x.routeNameC.split('>')[0].trim(),
                                    x.routeNameS.split('>')[0].trim(),
                                    x.routeNameE.split('>')[1].trim(),
                                    x.routeNameC.split('>')[1].trim(),
                                    x.routeNameS.split('>')[1].trim()
                                )
                            }
                            routes.addAll(newRoutes)
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