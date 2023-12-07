import data.Route
import data.Stop
import json_models.*
import okhttp3.OkHttpClient
import okhttp3.Request

val routes: MutableList<Route> = mutableListOf()
val stops: MutableList<Stop> = mutableListOf()
val okHttpClient = OkHttpClient()

fun main() {
    println("Getting KMB routes...")
    getRoutes(Company.kmb)

    println("Getting CTB routes...")
    getRoutes(Company.ctb)

    println("Getting NLB routes...")
    getRoutes(Company.nlb)

    // todo NLB MTRB routes
    println("Getting KMB stops...")
    getKmbStops()

    println("Getting CTB stops...")
    for (i in 1001..3823) { // todo get the stop number range from online source
        println("Requesting stop $i")
        getCTBStop(i) //todo make it async
    }
    // todo CTB NLB MTRB stops
    // todo get fare info, list of stops for a route
    // todo convert to JSON
    // todo proper log
}

fun getRoutes(company: Company) {
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
                        Route(company, x.route, x.bound, x.origEn, x.origTc, x.origSc, x.destEn, x.destTc, x.destSc)
                    }
                    routes.addAll(newRoutes)
                    println("Added ${newRoutes.size} ${company.name.uppercase()} routes")
                }
            }

            Company.ctb -> {
                val ctbRoutes = CtbRouteResponse.fromJson(response)?.data
                if (!ctbRoutes.isNullOrEmpty()) {
                    val newRoutes = ctbRoutes.map { x ->
                        Route(company, x.route, null, x.origEn, x.origTc, x.origSc, x.destEn, x.destTc, x.destSc)
                    }
                    routes.addAll(newRoutes)
                    println("Added ${newRoutes.size} ${company.name.uppercase()} routes")
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
                    println(newRoutes[0].destChiT)
                    println("Added ${newRoutes.size} ${company.name.uppercase()} routes")
                }
            }

            Company.mtrb -> TODO()
        }

    } catch (e: Exception) {
        println("Error occurred while getting ${company.name.uppercase()} routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
    }
}

fun getKmbStops() {
    try {
        val response = get(KMB_ALL_STOPS)
        val kmbStops = KmbStopResponse.fromJson(response)?.data
        if (!kmbStops.isNullOrEmpty()) {
            val newStops = kmbStops.map { x ->
                Stop(x.stop, x.nameEn, x.nameTc, x.nameSc, x.lat.toDouble(), x.long.toDouble())
            }
            stops.addAll(newStops)
        }
    } catch (e: Exception) {
        println("Error occurred while getting KMB stops \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
    }
}

fun getCTBStop(id: Int) {
    try {
        val response = get("$CTB_ALL_STOP/${String.format("%06d", id)}")
        val ctbStop = CtbStopResponse.fromJson(response)?.data
        if (ctbStop?.stop != null) {
            val newStop = Stop(
                ctbStop.stop,
                ctbStop.nameEn!!,
                ctbStop.nameTc!!,
                ctbStop.nameSc!!,
                ctbStop.lat!!.toDouble(),
                ctbStop.long!!.toDouble()
            )
            stops.add(newStop)
        }
    } catch (e: Exception) {
        println("Error occurred while getting CTB stop \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
    }
}

fun get(url: String): String {
    val request = Request.Builder().url(url).build()
    okHttpClient.newCall(request).execute().use {
        if (it.isSuccessful && it.body != null) {
            return it.body?.string() ?: ""
        } else {
            println(it.message) //todo
        }
    }
    return ""
}