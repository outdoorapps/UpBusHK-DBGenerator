import Paths.Companion.BUS_STOPS_SOURCE_PATH
import Paths.Companion.DB_EXPORT_PATH
import Paths.Companion.ROUTE_INFO_EXPORT_PATH
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import controllers.StopController.Companion.validateStops
import data.*
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
const val MAX_ERROR_DISTANCE_METERS = 150.0
suspend fun main() {
    val t = measureTime {
        loadData()
    }
    println(
        "Mapped Routes:${testData.routeInfos.size}, Requestable routes:${sharedData.requestableRoutes.size}, " +
                "Requestable stops:${stops.size}, loaded in $t"
    )

    val routes = mutableListOf<Route>()
    val kmbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.KMB }.toMutableList()
    val ctbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.CTB }.toMutableList()
    val nlbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.NLB }.toMutableList()
    println("KMB:${kmbRequestableRoutes.size}, CTB:${ctbRequestableRoutes.size}, NLB:${nlbRequestableRoutes.size}")

    val kmbNoMatch = mutableListOf<RequestableRoute>()
    val ctbJointRoutes = mutableListOf<RequestableRoute>()

    kmbRequestableRoutes.forEach {
        var routeInfo: RouteInfo? = null

        val origin = sharedData.requestableStops.find { stop -> stop.stopId == it.stops.first() }
        val dest = sharedData.requestableStops.find { stop -> stop.stopId == it.stops.last() }

        val candidates = testData.routeInfos.filter { info ->
            (info.companyCode.contains(it.company.value) || info.companyCode.contains("LWB")) && info.routeNameE == it.number
        }
        if (origin != null && dest != null) {
            routeInfo = candidates.find { info ->
                val routeInfoOrigin = stops.find { stop -> stop.stopId == info.stStopId }
                val routeInfoDest = stops.find { stop -> stop.stopId == info.edStopId }
                val originDistance = if (routeInfoOrigin != null) Utils.distanceInMeters(
                    origin.latLng,
                    routeInfoOrigin.latLng
                ) else Double.MAX_VALUE
                val destDistance = if (routeInfoDest != null) Utils.distanceInMeters(
                    dest.latLng,
                    routeInfoDest.latLng
                ) else Double.MAX_VALUE

//                if(it.number == "A41" && it.bound == Bound.O) {
//                    println("${it.number},${it.bound},${it.kmbServiceType}")
//                    println("ori:${origin.latLng}, ${routeInfoOrigin!!.latLng}")
//                    println(Utils.distanceInMeters(origin.latLng, routeInfoOrigin.latLng))
//                    println("dest:${dest.latLng}, ${routeInfoDest!!.latLng}")
//                    println(Utils.distanceInMeters(dest.latLng, routeInfoDest.latLng))
//                }
                originDistance <= MAX_ERROR_DISTANCE_METERS && destDistance <= MAX_ERROR_DISTANCE_METERS
            }
        }

        if (routeInfo != null) {
            testData.routeInfos.remove(routeInfo)
            if (routeInfo.companyCode.contains("+")) {
                val ctbRoute = ctbRequestableRoutes.find { x -> x.number == it.number && x.bound == it.bound }
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
    ctbJointRoutes.forEach { ctbRequestableRoutes.removeIf { e -> it.number == e.number && it.bound == e.bound } }

    println("KMB Routes added: ${routes.size}")
    println("kmbNoMatch: ${kmbNoMatch.size}")
    kmbNoMatch.forEach { print("${it.number}-${it.bound},") }
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
                    Stop(it.properties.stopId, LatLng(crsCoordinate.getLatitude(), crsCoordinate.getLongitude()))
                )
            }
            stops.sortBy { x -> x.stopId }
        }
    }
}