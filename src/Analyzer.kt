import Paths.Companion.BUS_STOPS_SOURCE_PATH
import Paths.Companion.DB_EXPORT_PATH
import Paths.Companion.ROUTE_INFO_EXPORT_PATH
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory
import com.programmerare.crsTransformations.coordinate.eastingNorthing
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
const val ROUTE_INFO_ERROR_DISTANCE_METERS = 150.0
const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0
suspend fun main() {
    val t = measureTime {
        loadData()
    }
    println(
        "Mapped Routes:${testData.routeInfos.size}, Requestable routes:${sharedData.requestableRoutes.size}, " + "Requestable stops:${stops.size}, loaded in $t"
    )

    val routes = mutableListOf<Route>()
    val kmbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.KMB }.toMutableList()
    val ctbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.CTB }.toMutableList()
    val nlbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.NLB }.toMutableList()
    println("KMB:${kmbRequestableRoutes.size}, CTB:${ctbRequestableRoutes.size}, NLB:${nlbRequestableRoutes.size}")

    val kmbUnmappedRoutes = mutableListOf<RequestableRoute>()
    val ctbUnmappedRoutes = mutableListOf<RequestableRoute>()
    val nlbUnmappedRoutes = mutableListOf<RequestableRoute>()
    val unmappedJointRoutes = mutableListOf<RequestableRoute>()
    val jointRoutes = mutableListOf<RequestableRoute>()

    // Match routeInfo
    kmbRequestableRoutes.forEach {
        // Merge KMB and CTB routes
        val routeInfo: RouteInfo? = getRouteInfo(it)
        val secondaryStops = mutableListOf<String>()
        if (isJointRoute(it)) {
            if (it.kmbServiceType == 1) {
                val ctbRoute = ctbRequestableRoutes.find { x ->
                    x.number == it.number && isRouteBoundMatch(x, it, JOINT_ROUTE_ERROR_DISTANCE_METERS)
                }
                if (ctbRoute == null) {
                    println("No CTB route matches KMB route: ${it.number},Bound:${it.bound},service type:${it.kmbServiceType}")
                } else {
                    if (it.stops.size == ctbRoute.stops.size) {
                        secondaryStops.addAll(ctbRoute.stops)
                    } else if (it.stops.size > ctbRoute.stops.size){
                        it.stops.forEach { kmbStopId ->
                            val kmbStop = sharedData.requestableStops.find { stop -> stop.stopId == kmbStopId }
//                            ctbRoute.stops.find { stop ->
//                                val ctbStop = sharedData.requestableStops.find { x -> x.stopId == kmbStopId }
//                                Utils.distanceInMeters(ctbStop.latLng, kmbStop.latLng) < 20.0
//                            }
                        }
                    } else {
                        //it.stops.size < ctbRoute.stops.size
                    }
                    // Remove merged route from CTB's list todo
                    // ctbRequestableRoutes.remove(ctbRoute)

                    // todo need to match CTB stops and put into the final Route item, match gps coordinates
                    jointRoutes.add(it)
                }
            } else {
                // TODO match stops
            }
        }
        if (routeInfo != null) {
            testData.routeInfos.remove(routeInfo)
        } else {
            if (isJointRoute(it)) unmappedJointRoutes.add(it) else kmbUnmappedRoutes.add(it)
        }
        routes.add(
            Route(
                routeInfo?.companyCode ?: it.company.value,
                it.number,
                it.bound,
                it.originEn,
                it.originChiT,
                it.originChiS,
                it.destEn,
                it.destChiT,
                it.destChiS,
                it.kmbServiceType,
                null,
                routeInfo?.routeId,
                routeInfo?.objectId,
                it.stops,
                emptyList()
            )
        )
    }
    println("KMB routes mapped: ${routes.size - kmbUnmappedRoutes.size}, unmapped: ${kmbUnmappedRoutes.size}")
    println("Joint routes: ${jointRoutes.size} mapped: ${jointRoutes.size - unmappedJointRoutes.size}, unmapped: ${unmappedJointRoutes.size}")

    ctbRequestableRoutes.forEach {
        val routeInfo: RouteInfo? = getRouteInfo(it)
        val candidates = getRouteInfoCandidates(it)
        val companyCode = if (candidates.isEmpty()) it.company.value else candidates.last().companyCode
        if (companyCode.contains("+")) println("$companyCode,${it.number},${it.bound}") //todo
        if (routeInfo != null) {
            testData.routeInfos.remove(routeInfo)
        } else {
            ctbUnmappedRoutes.add(it)
        }
        routes.add(
            Route(
                companyCode,
                it.number,
                it.bound,
                it.originEn,
                it.originChiT,
                it.originChiS,
                it.destEn,
                it.destChiT,
                it.destChiS,
                it.kmbServiceType,
                null,
                routeInfo?.routeId,
                routeInfo?.objectId,
                it.stops,
                emptyList()
            )
        )
    }
    println("CTB routes mapped: ${ctbRequestableRoutes.size - ctbUnmappedRoutes.size}, unmapped: ${ctbUnmappedRoutes.size}")

    nlbRequestableRoutes.forEach {
        val routeInfo: RouteInfo? = getRouteInfo(it)
        if (routeInfo != null) {
            testData.routeInfos.remove(routeInfo)
        } else {
            nlbUnmappedRoutes.add(it)
        }
        routes.add(
            Route(
                Company.NLB.value,
                it.number,
                it.bound,
                it.originEn,
                it.originChiT,
                it.originChiS,
                it.destEn,
                it.destChiT,
                it.destChiS,
                it.kmbServiceType,
                null,
                routeInfo?.routeId,
                routeInfo?.objectId,
                it.stops,
                emptyList()
            )
        )
    }
    println("NLB routes mapped: ${nlbRequestableRoutes.size - nlbUnmappedRoutes.size}, unmapped: ${nlbUnmappedRoutes.size}")
}

private fun getRouteInfoCandidates(requestableRoute: RequestableRoute): List<RouteInfo> =
    when (requestableRoute.company) {
        Company.KMB -> testData.routeInfos.filter { info ->
            (info.companyCode.contains(requestableRoute.company.value) || info.companyCode.contains("LWB")) && info.routeNameE == requestableRoute.number
        }

        Company.CTB -> testData.routeInfos.filter { info ->
            info.companyCode.contains(requestableRoute.company.value) && info.routeNameE == requestableRoute.number
        }

        Company.NLB -> testData.routeInfos.filter { info ->
            info.companyCode.contains(requestableRoute.company.value) && info.routeNameE == requestableRoute.number
        }

        Company.MTRB -> TODO()
    }

private fun getRouteInfo(requestableRoute: RequestableRoute): RouteInfo? =
    getRouteInfoCandidates(requestableRoute).find { info ->
        isRouteInfoBoundMatch(requestableRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS)
    }

private fun isJointRoute(requestableRoute: RequestableRoute): Boolean {
    val candidates = getRouteInfoCandidates(requestableRoute)
    return if (candidates.isEmpty()) false else candidates.first().companyCode.contains("+")
}

private fun isRouteBoundMatch(
    requestableRoute1: RequestableRoute, requestableRoute2: RequestableRoute, errorDistance: Double
): Boolean {
    val origin1 = sharedData.requestableStops.find { stop -> stop.stopId == requestableRoute1.stops.first() }
    val dest1 = sharedData.requestableStops.find { stop -> stop.stopId == requestableRoute1.stops.last() }
    val origin2 = sharedData.requestableStops.find { stop -> stop.stopId == requestableRoute2.stops.first() }
    val dest2 = sharedData.requestableStops.find { stop -> stop.stopId == requestableRoute2.stops.last() }
    val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
        origin1.latLng, origin2.latLng
    ) else Double.MAX_VALUE
    val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
        dest1.latLng, dest2.latLng
    ) else Double.MAX_VALUE
    return originDistance <= errorDistance || destDistance <= errorDistance
}

private fun isRouteInfoBoundMatch(
    requestableRoute: RequestableRoute, routeInfo: RouteInfo, errorDistance: Double
): Boolean {
    val origin1 = sharedData.requestableStops.find { stop -> stop.stopId == requestableRoute.stops.first() }
    val dest1 = sharedData.requestableStops.find { stop -> stop.stopId == requestableRoute.stops.last() }
    val origin2 = stops.find { stop -> stop.stopId == routeInfo.stStopId }
    val dest2 = stops.find { stop -> stop.stopId == routeInfo.edStopId }
    val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
        origin1.latLng, origin2.latLng
    ) else Double.MAX_VALUE
    val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
        dest2.latLng, dest2.latLng
    ) else Double.MAX_VALUE
    return originDistance <= errorDistance && destDistance <= errorDistance
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
            val data = klaxon.parse<SharedData>(jsonString)
            sharedData.requestableRoutes.addAll(data!!.requestableRoutes)
            sharedData.requestableStops.addAll(data.requestableStops)
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