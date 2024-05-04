import com.beust.klaxon.Klaxon
import data.CompanyBusData
import data.CompanyBusRoute
import data.GovernmentBusRoute
import data.TrackInfo
import json_model.BusRouteStopCollection
import json_model.GovernmentRouteStop
import util.Paths.Companion.TRACK_INFO_EXPORT_PATH
import util.Utils.Companion.execute
import java.io.File
import java.util.zip.GZIPInputStream

// Matches bus company data and government bus data
class RouteMatcher {
    companion object {
        val stopCoordinateMap = mutableMapOf<Int, List<Double>>()
        val governmentBusRoutes = mutableListOf<GovernmentBusRoute>()

        fun initialize() {
            if (stopCoordinateMap.isEmpty() && governmentBusRoutes.isEmpty()) {
                val busRouteStops = mutableListOf<GovernmentRouteStop>()
                execute("Parsing government bus route-stop data...") {
                    val file = File("resources/JSON_BUS.json")
                    var json: String
                    file.inputStream().use { input ->
                        json = input.bufferedReader().use { it.readText() }.replace("\uFEFF", "")
                    }
                    val routeStops = BusRouteStopCollection.fromJson(json)?.governmentRouteStops
                    if (!routeStops.isNullOrEmpty()) {
                        busRouteStops.addAll(routeStops)
                    }
                }
                execute("Converting into routes and stops...") {
                    busRouteStops.forEach { routeStop ->
                        val info = routeStop.info
                        stopCoordinateMap[info.stopID] = routeStop.geometry.longLatCoordinates

                        if (!governmentBusRoutes.any { it.routeId == info.routeID && it.routeSeq == info.routeSeq }) {
                            val routeStopsOfRoute =
                                busRouteStops.filter { it.info.routeID == info.routeID && it.info.routeSeq == info.routeSeq }
                                    .sortedBy { it.info.stopSeq }
                            if (routeStopsOfRoute.isNotEmpty()) {
                                val startStopId = routeStopsOfRoute.first().info.stopID
                                val endStopId = routeStopsOfRoute.last().info.stopID
                                governmentBusRoutes.add(
                                    GovernmentBusRoute(
                                        routeId = info.routeID,
                                        routeSeq = info.routeSeq,
                                        companyCode = info.companyCode,
                                        routeNameE = info.routeNameE,
                                        stStopId = startStopId,
                                        stStopNameE = info.locStartNameE,
                                        stStopNameC = info.locStartNameC,
                                        stStopNameS = info.locStartNameS,
                                        edStopId = endStopId,
                                        edStopNameE = info.locEndNameE,
                                        edStopNameS = info.locEndNameS,
                                        edStopNameC = info.locEndNameC,
                                        serviceMode = info.serviceMode,
                                        specialType = info.specialType,
                                        journeyTime = info.journeyTime,
                                        fullFare = info.fullFare
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                println("DataMater has been initialized")
            }
        }
    }

    fun compareWithTrackInfo(governmentBusRoutes: List<GovernmentBusRoute>) {
        val file = File(TRACK_INFO_EXPORT_PATH)
        var json: String
        file.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                gzInput.bufferedReader().use {
                    json = it.readText()
                }
            }
        }
        val trackInfoList = Klaxon().parseArray<TrackInfo>(json)!!
        println("TrackInfo items: ${trackInfoList.size}")

        trackInfoList.forEach { trackInfo ->
            val matchingRoutes =
                governmentBusRoutes.filter { it.routeId == trackInfo.routeId && it.routeSeq == trackInfo.routeSeq }
            if (matchingRoutes.isEmpty()) println("no matching route ${trackInfo.routeId},${trackInfo.routeSeq}")
            if (matchingRoutes.size > 1) println("more than one matching route ${trackInfo.routeId},${trackInfo.routeSeq} => $matchingRoutes")
            if (matchingRoutes.size == 1) {
                val matchingRoute = matchingRoutes.first()
                if (trackInfo.stStopId != matchingRoute.stStopId)
                    println("Starting stop doesn't match (${trackInfo.routeId},${trackInfo.routeSeq}): ${trackInfo.stStopId},${matchingRoute.stStopId}")
                if (trackInfo.edStopId != matchingRoute.edStopId)
                    println("Starting stop doesn't match (${trackInfo.routeId},${trackInfo.routeSeq}): ${trackInfo.edStopId},${matchingRoute.edStopId}")
            }
        }
    }
}

fun main() {
    // 1. Load all necessary data
    RouteMatcher.initialize()
    val stopCoordinateMap = RouteMatcher.stopCoordinateMap
    val governmentBusRoutes = RouteMatcher.governmentBusRoutes

    var companyBusData = CompanyBusData()
    execute("Loading saved company bus data...") {
        companyBusData = getBusCompanyData()
    }
    val companyBusRoutes = companyBusData.companyBusRoutes

//    val matchingData = mutableMapOf<CompanyBusRoute, GovernmentBusRoute?>()
//    companyBusRoutes.forEach { companyBusRoute ->
//        governmentBusRoutes.filter { it.routeNameE == companyBusRoute.number && company }
//        matchingData[companyBusRoute] = null
//    }

}