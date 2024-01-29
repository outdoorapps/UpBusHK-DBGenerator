import Paths.Companion.BUS_STOPS_SOURCE_PATH
import Paths.Companion.DB_EXPORT_PATH
import Paths.Companion.DB_FINAL_EXPORT_PATH
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
import java.math.RoundingMode
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.time.measureTime

val testData = TestData()

class Analyzer(private val govRecordStops: MutableList<GovRecordStop>) {
    companion object {
        const val ROUTE_INFO_ERROR_DISTANCE_METERS = 150.0
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0
    }

    private val kmbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.KMB }
    private val ctbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.CTB }
    private val nlbRequestableRoutes = sharedData.requestableRoutes.filter { it.company == Company.NLB }
    private val jointRouteNumbers = mutableListOf<String>()
    val routes = mutableListOf<Route>()

    // For stats
    private val kmbUnmappedRoutes = mutableListOf<RequestableRoute>()
    private val ctbUnmappedRoutes = mutableListOf<RequestableRoute>()
    private val nlbUnmappedRoutes = mutableListOf<RequestableRoute>()
    private val unmappedJointRoutes = mutableListOf<RequestableRoute>()
    private val jointRoutes = mutableListOf<RequestableRoute>()

    init {
        testData.routeInfos.forEach {
            if (it.companyCode.contains("+") && !jointRouteNumbers.contains(it.routeNameE)) {
                jointRouteNumbers.add(it.routeNameE)
            }
        }
        println("KMB:${kmbRequestableRoutes.size}, CTB:${ctbRequestableRoutes.size}, NLB:${nlbRequestableRoutes.size}, Joint (unique route number):${jointRouteNumbers.size}")
    }

    private fun isJointRoute(requestableRoute: RequestableRoute): Boolean =
        jointRouteNumbers.contains(requestableRoute.number)

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
        val origin2 = govRecordStops.find { stop -> stop.stopId == routeInfo.stStopId }
        val dest2 = govRecordStops.find { stop -> stop.stopId == routeInfo.edStopId }
        val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1.latLng, origin2.latLng
        ) else Double.MAX_VALUE
        val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
            dest2.latLng, dest2.latLng
        ) else Double.MAX_VALUE
        return originDistance <= errorDistance && destDistance <= errorDistance
    }

//    private fun isStopNameMatch(stopName1: String, stopName2: String): Boolean {
//        val regex = "\\p{P}".toRegex()
//        val delimiter = "[, ]+".toRegex()
//        val names1 = stopName1.replace(regex, "").split(delimiter)
//        val names2 = stopName2.replace(regex, "").split(delimiter)
//        return stopName1.contains(stopName2) || stopName2.contains(stopName1) || names1.any { stopName2.contains(it) } || names2.any {
//            stopName1.contains(
//                it
//            )
//        }
//    }

    private fun getClosestStopID(stop: RequestableStop, candidateStopIDs: List<String>): String? {
        var result: String? = null
        var minDistance = Double.MAX_VALUE
        candidateStopIDs.forEach {
            val candidateStop = sharedData.requestableStops.find { x -> x.stopId == it }
            if (candidateStop != null) {
                val distance = Utils.distanceInMeters(candidateStop.latLng, stop.latLng)
                if (distance < minDistance) {
                    minDistance = distance
                    result = candidateStop.stopId
                }
            }
        }
        return result
    }

    private fun getStopMap(refRoute: RequestableRoute, matchingRoute: RequestableRoute): Map<String, String> {
        val stopMap = mutableMapOf<String, String>()
        refRoute.stops.forEach { kmbStopId ->
            val refStop = sharedData.requestableStops.find { x -> x.stopId == kmbStopId }
            // Search a sublist of remaining stops
            val startIndex = if (stopMap.isEmpty()) {
                0
            } else {
                val index = matchingRoute.stops.indexOf(stopMap.values.last())
                if (index == matchingRoute.stops.size) index else index + 1
            }

            val ctbStopId = if (refStop != null) {
                getClosestStopID(refStop, matchingRoute.stops.subList(startIndex, matchingRoute.stops.size))
            } else null
            stopMap[kmbStopId] = ctbStopId ?: ""
        }
        if (stopMap.values.contains("")) {
            stopMap.filter { (_, matchingStopId) -> matchingStopId == "" }
                .forEach { (refStopId, _) -> println("Not match for StopID:$refStopId, (${refRoute.number},${refRoute.bound},${refRoute.kmbServiceType})") }
        }
        return stopMap
    }

    fun analyze() {
        // Match routeInfo
        kmbRequestableRoutes.forEach { kmbRoute ->
            // Merge KMB and CTB routes
            val routeInfo: RouteInfo? = getRouteInfo(kmbRoute)
            val secondaryStops = mutableListOf<String>()
            if (isJointRoute(kmbRoute)) {
                val ctbRoute = ctbRequestableRoutes.find { x ->
                    x.number == kmbRoute.number && isRouteBoundMatch(x, kmbRoute, JOINT_ROUTE_ERROR_DISTANCE_METERS)
                }
                if (ctbRoute == null) {
                    println("No CTB route matches KMB route: ${kmbRoute.number},Bound:${kmbRoute.bound},service type:${kmbRoute.kmbServiceType}")
                } else {
                    secondaryStops.addAll(getStopMap(kmbRoute, ctbRoute).values)
                    jointRoutes.add(kmbRoute)
                }
            }
            if (routeInfo != null) {
                testData.routeInfos.remove(routeInfo)
            } else {
                if (isJointRoute(kmbRoute)) unmappedJointRoutes.add(kmbRoute) else kmbUnmappedRoutes.add(kmbRoute)
            }
            routes.add(
                Route(
                    routeInfo?.companyCode ?: kmbRoute.company.value,
                    kmbRoute.number,
                    kmbRoute.bound,
                    kmbRoute.originEn,
                    kmbRoute.originChiT,
                    kmbRoute.originChiS,
                    kmbRoute.destEn,
                    kmbRoute.destChiT,
                    kmbRoute.destChiS,
                    kmbRoute.kmbServiceType,
                    null,
                    routeInfo?.routeId,
                    routeInfo?.objectId,
                    kmbRoute.stops,
                    secondaryStops
                )
            )
        }

        ctbRequestableRoutes.filter { !isJointRoute(it) }.forEach {
            val routeInfo: RouteInfo? = getRouteInfo(it)
            val candidates = getRouteInfoCandidates(it)
            val companyCode = if (candidates.isEmpty()) it.company.value else candidates.last().companyCode
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
        println("KMB routes: ${routes.filter { it.companyCode == "KMB" || it.companyCode == "LWB" }.size} (" + "mapped: ${routes.filter { it.companyCode == "KMB" || it.companyCode == "LWB" }.size - kmbUnmappedRoutes.size}, " + "unmapped: ${kmbUnmappedRoutes.size})"
        )
        println("CTB routes: ${routes.filter { it.companyCode == "CTB" }.size} (" + "mapped: ${routes.filter { it.companyCode == "CTB" }.size - ctbUnmappedRoutes.size}, unmapped: ${ctbUnmappedRoutes.size})"
        )
        println("NLB routes: ${routes.filter { it.companyCode == "NLB" }.size} (" + " mapped: ${routes.filter { it.companyCode == "NLB" }.size - nlbUnmappedRoutes.size}, unmapped: ${nlbUnmappedRoutes.size})"
        )
        println(
            "Joint routes: ${routes.filter { it.companyCode.contains("+") }.size} (" + "mapped: ${jointRoutes.size - unmappedJointRoutes.size}, unmapped: ${unmappedJointRoutes.size})"
        )
        println("Total routes: ${routes.size}")
    }
}

suspend fun main() {
    val govRecordStops = mutableListOf<GovRecordStop>()
    val t = measureTime {
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
                    govRecordStops.add(
                        GovRecordStop(
                            it.properties.stopId,
                            mutableListOf(crsCoordinate.getLatitude(), crsCoordinate.getLongitude())
                        )
                    )
                }
                govRecordStops.sortBy { x -> x.stopId }
            }
        }
    }
    println(
        "Mapped Routes:${testData.routeInfos.size}, Requestable routes:${sharedData.requestableRoutes.size}, Stops (Government record):${govRecordStops.size}, loaded in $t"
    )
    val analyzer = Analyzer(govRecordStops)
    execute("Analyzing...") {
        println()
        analyzer.analyze()
    }

    execute("Rounding LatLng...") {
        val stops = sharedData.requestableStops.map {
            val lat = it.latLng[0].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            val long = it.latLng[1].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            it.copy(latLng = mutableListOf(lat, long))
        }
        sharedData.requestableStops.clear()
        sharedData.requestableStops.addAll(stops)
    }


    execute("Writing final database \"$DB_FINAL_EXPORT_PATH\"...") {
        writeToFile(FinalDatabase(analyzer.routes, sharedData.requestableStops).toJson(), DB_FINAL_EXPORT_PATH)
    }
}

data class FinalDatabase(val routes: List<Route>, val requestableStops: List<RequestableStop>) {
    fun toJson() = Klaxon().toJsonString(this)
}