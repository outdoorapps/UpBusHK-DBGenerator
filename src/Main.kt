import data.Route
import data.Stop
import json_models.KmbRouteResponse
import json_models.KmbStopResponse
import okhttp3.OkHttpClient
import okhttp3.Request

val routes: MutableList<Route> = mutableListOf()
val stops: MutableList<Stop> = mutableListOf()

fun main() {
    getKmbRoute()
    getKmbStops()
    println("Routes:${routes.size} ")
    println("Stops:${stops.size} ")
}

fun getKmbStops() {
    val client = OkHttpClient()
    val request = Request.Builder().url(KMB_ALL_STOPS).build()
    val response = client.newCall(request).execute()
    if (response.isSuccessful && response.body != null) {
        val kmbStops = KmbStopResponse.fromJson(response.body!!.string())?.data
        if (!kmbStops.isNullOrEmpty()) {
            val newStops =
                kmbStops.map { Stop(it.stop, it.nameEn, it.nameTc, it.nameSc, it.lat.toDouble(), it.long.toDouble()) }
            stops.addAll(newStops)
        }
    } else {
        throw Exception() //todo
    }
}

fun getKmbRoute() {
    val client = OkHttpClient()
    val request = Request.Builder().url(KMB_ALL_ROUTES).build()
    val response = client.newCall(request).execute()
    if (response.isSuccessful && response.body != null) {
        val kmbRoutes = KmbRouteResponse.fromJson(response.body!!.string())?.data
        if (!kmbRoutes.isNullOrEmpty()) {
            val newRoutes =
                kmbRoutes.map {
                    Route(
                        Company.kmb,
                        it.route,
                        it.bound,
                        it.origEn,
                        it.origTc,
                        it.origSc,
                        it.destEn,
                        it.destTc,
                        it.destSc
                    )
                }
            routes.addAll(newRoutes)
        }

    } else {
        throw Exception() //todo
    }
}