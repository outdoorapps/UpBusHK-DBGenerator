import Analyzer.Companion.intermediates
import com.beust.klaxon.Klaxon
import data.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import utils.Company
import utils.Paths.Companion.ARCHIVE_EXPORT_PATH
import utils.Paths.Companion.DB_PATHS_EXPORT_PATH
import utils.Paths.Companion.DB_ROUTES_STOPS_EXPORT_PATH
import utils.Paths.Companion.REQUESTABLES_EXPORT_PATH
import utils.Paths.Companion.ROUTE_INFO_EXPORT_PATH
import utils.Utils
import utils.Utils.Companion.execute
import utils.Utils.Companion.loadGovRecordStop
import utils.Utils.Companion.writeToJsonFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.RoundingMode
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime

class Analyzer(private val routeInfos: MutableList<RouteInfo>, private val govRecordStops: MutableList<GovRecordStop>) {
    companion object {
        const val ROUTE_INFO_ERROR_DISTANCE_METERS = 150.0
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0
        val intermediates = listOf(DB_ROUTES_STOPS_EXPORT_PATH, DB_PATHS_EXPORT_PATH)
    }

    private val kmbRequestableRoutes = requestables.requestableRoutes.filter { it.company == Company.KMB }
    private val ctbRequestableRoutes = requestables.requestableRoutes.filter { it.company == Company.CTB }
    private val nlbRequestableRoutes = requestables.requestableRoutes.filter { it.company == Company.NLB }
    private val jointRouteNumbers = mutableSetOf<String>()
    val routes = mutableListOf<Route>()

    // For stats
    private val unmappedKmbRoutes = mutableListOf<RequestableRoute>()
    private val unmappedCtbRoutes = mutableListOf<RequestableRoute>()
    private val unmappedNlbRoutes = mutableListOf<RequestableRoute>()
    private val unmappedJointRoutes = mutableListOf<RequestableRoute>()
    private val jointRoutes = mutableListOf<RequestableRoute>()

    init {
        routeInfos.forEach {
            if (it.companyCode.contains("+")) {
                jointRouteNumbers.add(it.routeNameE)
            }
        }
        println("KMB:${kmbRequestableRoutes.size}, CTB:${ctbRequestableRoutes.size}, NLB:${nlbRequestableRoutes.size}, Joint (unique route number):${jointRouteNumbers.size}")
    }

    private fun isJointRoute(requestableRoute: RequestableRoute): Boolean =
        jointRouteNumbers.contains(requestableRoute.number)

    private fun getRouteInfoCandidates(requestableRoute: RequestableRoute): List<RouteInfo> =
        when (requestableRoute.company) {
            Company.KMB -> routeInfos.filter { info ->
                (info.companyCode.contains(requestableRoute.company.value) || info.companyCode.contains("LWB")) && info.routeNameE == requestableRoute.number
            }

            Company.CTB -> routeInfos.filter { info ->
                info.companyCode.contains(requestableRoute.company.value) && info.routeNameE == requestableRoute.number
            }

            Company.NLB -> routeInfos.filter { info ->
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
        val origin1 = requestables.requestableStops.find { stop -> stop.stopId == requestableRoute1.stops.first() }
        val dest1 = requestables.requestableStops.find { stop -> stop.stopId == requestableRoute1.stops.last() }
        val origin2 = requestables.requestableStops.find { stop -> stop.stopId == requestableRoute2.stops.first() }
        val dest2 = requestables.requestableStops.find { stop -> stop.stopId == requestableRoute2.stops.last() }
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
        val origin1 = requestables.requestableStops.find { stop -> stop.stopId == requestableRoute.stops.first() }
        val dest1 = requestables.requestableStops.find { stop -> stop.stopId == requestableRoute.stops.last() }
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

    private fun getClosestStopID(stop: RequestableStop, candidateStopIDs: List<String>): String? {
        var result: String? = null
        var minDistance = Double.MAX_VALUE
        candidateStopIDs.forEach {
            val candidateStop = requestables.requestableStops.find { x -> x.stopId == it }
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
            val refStop = requestables.requestableStops.find { x -> x.stopId == kmbStopId }
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
                routeInfos.remove(routeInfo)
            } else {
                if (isJointRoute(kmbRoute)) unmappedJointRoutes.add(kmbRoute) else unmappedKmbRoutes.add(kmbRoute)
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
                routeInfos.remove(routeInfo)
            } else {
                unmappedCtbRoutes.add(it)
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
                    routeInfo?.objectId,
                    it.stops,
                    emptyList()
                )
            )
        }

        nlbRequestableRoutes.forEach {
            val routeInfo: RouteInfo? = getRouteInfo(it)
            if (routeInfo != null) {
                routeInfos.remove(routeInfo)
            } else {
                unmappedNlbRoutes.add(it)
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
                    routeInfo?.objectId,
                    it.stops,
                    emptyList()
                )
            )
        }
        println("- KMB routes: ${routes.filter { it.companyCode == "KMB" || it.companyCode == "LWB" }.size} (" + "mapped: ${routes.filter { it.companyCode == "KMB" || it.companyCode == "LWB" }.size - unmappedKmbRoutes.size}, " + "unmapped: ${unmappedKmbRoutes.size})"
        )
        println("- CTB routes: ${routes.filter { it.companyCode == "CTB" }.size} (mapped: ${routes.filter { it.companyCode == "CTB" }.size - unmappedCtbRoutes.size}, unmapped: ${unmappedCtbRoutes.size})"
        )
        println("- NLB routes: ${routes.filter { it.companyCode == "NLB" }.size} (mapped: ${routes.filter { it.companyCode == "NLB" }.size - unmappedNlbRoutes.size}, unmapped: ${unmappedNlbRoutes.size})"
        )
        println(
            "- Joint routes: ${routes.filter { it.companyCode.contains("+") }.size} (mapped: ${jointRoutes.size - unmappedJointRoutes.size}, unmapped: ${unmappedJointRoutes.size})"
        )
        val totalUnmapped =
            unmappedKmbRoutes.size + unmappedCtbRoutes.size + unmappedNlbRoutes.size + unmappedJointRoutes.size
        println("- Total routes: ${routes.size} (mapped: ${routes.size - totalUnmapped}, unmapped: $totalUnmapped)")
    }
}

suspend fun runAnalyzer() {
    val govRecordStops = mutableListOf<GovRecordStop>()
    val routeInfos = mutableListOf<RouteInfo>()
    val t = measureTime {
        coroutineScope {
            launch {
                val klaxon = Klaxon()
                val file = File(ROUTE_INFO_EXPORT_PATH)
                val stream = GZIPInputStream(file.inputStream())
                val jsonString = stream.bufferedReader().use { it.readText() }
                routeInfos.addAll(klaxon.parseArray<RouteInfo>(jsonString)!!.toList())
            }
            launch {
                val klaxon = Klaxon()
                val dbFile = File(REQUESTABLES_EXPORT_PATH)
                val dbStream = GZIPInputStream(dbFile.inputStream())
                val jsonString = dbStream.bufferedReader().use { it.readText() }
                val data = klaxon.parse<Requestables>(jsonString)
                requestables.requestableRoutes.addAll(data!!.requestableRoutes)
                requestables.requestableStops.addAll(data.requestableStops)
            }
            launch {
                govRecordStops.addAll(loadGovRecordStop())
            }
        }
    }
    println(
        "Mapped Routes:${routeInfos.size}, Requestable routes:${requestables.requestableRoutes.size}, Stops (Government record):${govRecordStops.size}, loaded in $t"
    )

    val analyzer = Analyzer(routeInfos, govRecordStops)
    execute("Analyzing...", true) { analyzer.analyze() }

    execute("Rounding LatLng...") {
        val stops = requestables.requestableStops.map {
            val lat = it.latLng[0].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            val long = it.latLng[1].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            it.copy(latLng = mutableListOf(lat, long))
        }
        requestables.requestableStops.clear()
        requestables.requestableStops.addAll(stops)
    }

    execute("Writing routes and stops \"$DB_ROUTES_STOPS_EXPORT_PATH\"...") {
        writeToJsonFile(
            RoutesStopsDatabase(analyzer.routes, requestables.requestableStops).toJson(), DB_ROUTES_STOPS_EXPORT_PATH
        )
    }

    execute("Writing paths \"$DB_PATHS_EXPORT_PATH\"...", true) {
        val pathIDs = mutableSetOf<Int>()
        analyzer.routes.forEach { if (it.pathId != null) pathIDs.add(it.pathId) }
        MappedRouteParser.parseFile(parseRouteInfo = true, parsePaths = true, pathIDsToWrite = pathIDs)
    }

    execute("Compressing files to archive...") {
        val output = FileOutputStream(ARCHIVE_EXPORT_PATH)
        val xzOStream = XZOutputStream(output, LZMA2Options())
        TarArchiveOutputStream(xzOStream).use {
            intermediates.forEach { path ->
                val file = File(path)
                FileInputStream(file).use { input ->
                    val entry = TarArchiveEntry(file.name)
                    entry.size = file.length()
                    it.putArchiveEntry(entry)
                    input.copyTo(it)
                    it.closeArchiveEntry()
                }
            }
        }
    }

    execute("Cleaning up intermediates...") { intermediates.forEach { File(it).delete() } }
}

suspend fun main() {
    runAnalyzer()
}