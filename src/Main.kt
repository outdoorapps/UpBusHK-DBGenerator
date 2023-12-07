import data.Route
import data.Stop
import json_models.CtbRouteResponse
import json_models.KmbRouteResponse
import json_models.KmbStopResponse
import okhttp3.OkHttpClient
import okhttp3.Request

val routes: MutableList<Route> = mutableListOf()
val stops: MutableList<Stop> = mutableListOf()
val okHttpClient = OkHttpClient()

fun main() {
    println("Getting KMB routes...")
    getKmbRoutes()

    println("Getting CTB routes...")
    getCtbRoutes()

    // todo NLB MTRB routes
    println("Getting KMB stops...")
    getKmbStops()

    // todo CTB NLB MTRB stops
    // todo get fare info, list of stops for a route
    // todo convert to JSON
    // todo proper log
}

// todo handle exception, null pointer
fun getKmbStops() {
    try {
        val request = Request.Builder().url(KMB_ALL_STOPS).build()
        okHttpClient.newCall(request).execute().use {
            if (it.isSuccessful && it.body != null) {
                val kmbStops = KmbStopResponse.fromJson(it.body!!.string())?.data
                if (!kmbStops.isNullOrEmpty()) {
                    val newStops = kmbStops.map { x ->
                        Stop(x.stop, x.nameEn, x.nameTc, x.nameSc, x.lat.toDouble(), x.long.toDouble())
                    }
                    stops.addAll(newStops)
                }
            }
        }
    } catch (e: Exception) {
        println("Error occurred while getting KMB stops \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
    }
}

fun getKmbRoutes() {
    try {
        val request = Request.Builder().url(KMB_ALL_ROUTES).build()
        okHttpClient.newCall(request).execute().use {
            if (it.isSuccessful && it.body != null) {
                val kmbRoutes = KmbRouteResponse.fromJson(it.body!!.string())?.data
                if (!kmbRoutes.isNullOrEmpty()) {
                    val newRoutes = kmbRoutes.map { x ->
                        Route(Company.kmb, x.route, x.bound, x.origEn, x.origTc, x.origSc, x.destEn, x.destTc, x.destSc)
                    }
                    routes.addAll(newRoutes)
                    println("Added ${newRoutes.size} KMB routes")
                }
            }
        }
    } catch (e: Exception) {
        println("Error occurred while getting KMB routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
    }
}

fun getCtbRoutes() {
    try {
        val request = Request.Builder().url(CTB_ALL_ROUTES).build()
        okHttpClient.newCall(request).execute().use {
            if (it.isSuccessful && it.body != null) {
                val ctbRoutes = CtbRouteResponse.fromJson(it.body!!.string())?.data
                if (!ctbRoutes.isNullOrEmpty()) {
                    val newRoutes = ctbRoutes.map { x ->
                        Route(Company.ctb, x.route, null, x.origEn, x.origTc, x.origSc, x.destEn, x.destTc, x.destSc)
                    }
                    routes.addAll(newRoutes)
                    println("Added ${newRoutes.size} CTB routes")
                }
            }
        }
    } catch (e: Exception) {
        println("Error occurred while getting CTB routes \"${object {}.javaClass.enclosingMethod.name}\" : " + e.stackTraceToString())
    }
}