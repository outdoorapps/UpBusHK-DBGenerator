import Paths.Companion.BUS_STOPS_SOURCE_PATH
import Paths.Companion.DB_EXPORT_PATH
import Paths.Companion.ROUTE_INFO_EXPORT_PATH
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import com.programmerare.crsTransformations.coordinate.northingEasting
import data.RequestableRoute
import data.Route
import data.Stop
import data.TestData
import json_models.BusStopRaw
import json_models.RouteInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.time.measureTime

val testData = TestData()
val stops = mutableListOf<Stop>()
const val MAX_ERROR_DISTANCE_METERS = 130.0 //todo 220 top at, 2E should have matched
suspend fun main() {
    val t = measureTime {
        loadData()
    }
    println("Mapped Routes:${testData.routeInfos.size}, Requestable:${sharedData.requestableRoutes.size}, Bus stops:${stops.size}, loaded in $t")

    val routes = mutableListOf<Route>()
    val kmbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.KMB }.toMutableList()
    val ctbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.CTB }.toMutableList()
    val nlbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.NLB }.toMutableList()

    val kmbNoMatch = mutableListOf<RequestableRoute>()
    val ctbJointRoutes = mutableListOf<RequestableRoute>()

    println("KMB:${kmbRequestableRoutes.size}")//todo

    kmbRequestableRoutes.forEach {
        // 1. Determine routeSeq (1 or 2)
        var routeInfo: RouteInfo? = null

        val origin = sharedData.requestableStops.find { stop -> stop.stopId == it.stops.first() }
        val dest = sharedData.requestableStops.find { stop -> stop.stopId == it.stops.last() }


        val routeInfo1 = testData.routeInfos.find { info ->
            (info.companyCode.contains(it.company.value) || info.companyCode.contains("LWB")) && info.routeNameE == it.number && info.routeSeq == 1
        }
        val routeInfo2 = testData.routeInfos.find { info ->
            (info.companyCode.contains(it.company.value) || info.companyCode.contains("LWB")) && info.routeNameE == it.number && info.routeSeq == 2
        }

        if (routeInfo1 != null) {
            val routeInfoOrigin1 = stops.find { stop -> stop.stopId == routeInfo1.stStopId }
            val routeInfoDest1 = stops.find { stop -> stop.stopId == routeInfo1.edStopId }

//            println("${origin!!.lat}, ${origin.long}, ${routeInfoOrigin1!!.latLng[0]}, ${routeInfoOrigin1.latLng[1]}")
//            println(Utils.distanceInMeters(
//                origin!!.lat, origin.long, routeInfoOrigin1!!.latLng[0], routeInfoOrigin1.latLng[1]))

            if (origin != null && routeInfoOrigin1 != null && Utils.distanceInMeters(
                    origin.lat, origin.long, routeInfoOrigin1.latLng[0], routeInfoOrigin1.latLng[1]
                ) <= MAX_ERROR_DISTANCE_METERS
            ) routeInfo = routeInfo1

            if (dest != null && routeInfoDest1 != null && Utils.distanceInMeters(
                    dest.lat, dest.long, routeInfoDest1.latLng[0], routeInfoDest1.latLng[1]
                ) <= MAX_ERROR_DISTANCE_METERS
            ) routeInfo = routeInfo1
        } else if (routeInfo2 != null) {
            val routeInfoOrigin2 = stops.find { stop -> stop.stopId == routeInfo2.stStopId }
            val routeInfoDest2 = stops.find { stop -> stop.stopId == routeInfo2.edStopId }
            if (origin != null && routeInfoOrigin2 != null && Utils.distanceInMeters(
                    origin.lat, origin.long, routeInfoOrigin2.latLng[0], routeInfoOrigin2.latLng[1]
                ) <= MAX_ERROR_DISTANCE_METERS
            ) routeInfo = routeInfo2

            if (dest != null && routeInfoDest2 != null && Utils.distanceInMeters(
                    dest.lat, dest.long, routeInfoDest2.latLng[0], routeInfoDest2.latLng[1]
                ) <= MAX_ERROR_DISTANCE_METERS
            ) routeInfo = routeInfo2
        }


        if (routeInfo != null) {
            testData.routeInfos.remove(routeInfo)
            if (routeInfo.companyCode.contains("+")) {
                val ctbRoute = ctbRequestableRoutes.find { x -> x.number == it.number }
                if (ctbRoute == null) {
                    println("No match for CTB joint route: ${it.number}")
                } else {
                    ctbJointRoutes.add(it)
                }
            }
            routes.add(
                Route(
                    routeInfo.companyCode,
                    it.number,
                    it.bound,
                    routeInfo.stStopId,
                    it.originEn,
                    it.originChiT,
                    it.originChiS,
                    routeInfo.edStopId,
                    it.destEn,
                    it.destChiT,
                    it.destChiS,
                    it.kmbServiceType,
                    null,
                    routeInfo.routeId,
                    routeInfo.objectId
                )
            )
        } else {
            // todo add route
            kmbNoMatch.add(it)
        }
    }
    // Remove merged route from CTB's list
    ctbJointRoutes.forEach { ctbRequestableRoutes.removeIf { e -> it.number == e.number } }

    println("Routes added: ${routes.size}")
    println("kmbNoMatch: ${kmbNoMatch.size}")
    kmbNoMatch.forEach { print("${it.number},") }
    println("\nctbRequestableRoutes: ${ctbRequestableRoutes.size}")
}

suspend fun loadData() {
    coroutineScope {
        launch {
            val klaxon = Klaxon()
            val file = File(ROUTE_INFO_EXPORT_PATH)
            val stream = GZIPInputStream(file.inputStream())
            val jsonString = stream.bufferedReader().use { it.readText() }
            val routeInfos = klaxon.parse<TestData>(jsonString)!!.routeInfos
            testData.routeInfos.addAll(routeInfos)
        }
        launch {
            val klaxon = Klaxon()
            val dbFile = File(DB_EXPORT_PATH)
            val dbStream = GZIPInputStream(dbFile.inputStream())
            val jsonString = dbStream.bufferedReader().use { it.readText() }
            val routes = klaxon.parse<SharedData>(jsonString)!!.requestableRoutes
            val stops = klaxon.parse<SharedData>(jsonString)!!.requestableStops
            sharedData.requestableRoutes.addAll(routes)
            sharedData.requestableStops.addAll(stops)
        }
        launch {
            val crsTransformationAdapter =
                CrsTransformationAdapterCompositeFactory.createCrsTransformationFirstSuccess()
            val klaxon = Klaxon()
            val file = ZipFile(BUS_STOPS_SOURCE_PATH)
            val stream = file.getInputStream(file.entries().nextElement())
            val jsonString = stream.bufferedReader().use { it.readText() }
            val busStopFeature = klaxon.parse<BusStopRaw>(jsonString)!!.features
            busStopFeature.forEach {
                val crsCoordinate = crsTransformationAdapter.transformToCoordinate(
                    eastingNorthing(
                        it.geometry.coordinates[0].toDouble(),
                        it.geometry.coordinates[1].toDouble(),
                        EpsgNumber.CHINA__HONG_KONG__HONG_KONG_1980_GRID_SYSTEM__2326
                    ), EpsgNumber.WORLD__WGS_84__4326
                )
                stops.add(
                    Stop(
                        it.properties.stopId, listOf(crsCoordinate.getLatitude(), crsCoordinate.getLongitude())
                    )
                )
            }
            stops.sortBy { x -> x.stopId }
        }
    }
}