import Analyzer.Companion.intermediates
import com.beust.klaxon.Klaxon
import data.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import utils.Company
import utils.Paths.Companion.ARCHIVE_NAME
import utils.Paths.Companion.DB_PATHS_EXPORT_PATH
import utils.Paths.Companion.DB_ROUTES_STOPS_EXPORT_PATH
import utils.Paths.Companion.REQUESTABLES_EXPORT_PATH
import utils.Paths.Companion.ROUTE_INFO_EXPORT_PATH
import utils.Utils
import utils.Utils.Companion.execute
import utils.Utils.Companion.getCompanies
import utils.Utils.Companion.isSolelyOfCompany
import utils.Utils.Companion.loadGovRecordStop
import utils.Utils.Companion.writeToArchive
import utils.Utils.Companion.writeToJsonFile
import java.io.File
import java.math.RoundingMode
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime

class Analyzer(
    private val requestedData: RequestedData,
    private val routeInfos: MutableList<RouteInfo>,
    private val govStops: MutableList<GovStop>
) {
    companion object {
        const val ROUTE_INFO_ERROR_DISTANCE_METERS = 150.0
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0
        val intermediates = listOf(DB_ROUTES_STOPS_EXPORT_PATH, DB_PATHS_EXPORT_PATH)
    }

    private val kmbRequestableRoutes = requestedData.companyRoutes.filter { it.company == Company.KMB }
    private val ctbRequestableRoutes = requestedData.companyRoutes.filter { it.company == Company.CTB }
    private val nlbRequestableRoutes = requestedData.companyRoutes.filter { it.company == Company.NLB }
    private val jointRouteNumbers = mutableSetOf<String>()
    val routes = mutableListOf<Route>()

    // For stats
    private val unmappedKmbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedCtbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedNlbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedJointRoutes = mutableListOf<CompanyRoute>()
    private val jointRoutes = mutableListOf<CompanyRoute>()

    init {
        routeInfos.forEach {
            if (it.companyCode.contains("+")) {
                jointRouteNumbers.add(it.routeNameE)
            }
        }
        println("KMB:${kmbRequestableRoutes.size}, CTB:${ctbRequestableRoutes.size}, NLB:${nlbRequestableRoutes.size}, Joint (unique route number):${jointRouteNumbers.size}")
    }

    private fun isJointRoute(companyRoute: CompanyRoute): Boolean = jointRouteNumbers.contains(companyRoute.number)

    private fun getRouteInfoCandidates(companyRoute: CompanyRoute): List<RouteInfo> = when (companyRoute.company) {
        Company.KMB -> routeInfos.filter { info ->
            (info.companyCode.contains(companyRoute.company.value) || info.companyCode.contains("LWB")) && info.routeNameE == companyRoute.number
        }

        Company.CTB -> routeInfos.filter { info ->
            info.companyCode.contains(companyRoute.company.value) && info.routeNameE == companyRoute.number
        }

        Company.NLB -> routeInfos.filter { info ->
            info.companyCode.contains(companyRoute.company.value) && info.routeNameE == companyRoute.number
        }

        Company.MTRB -> TODO()
    }

    private fun getRouteInfo(companyRoute: CompanyRoute): RouteInfo? =
        getRouteInfoCandidates(companyRoute).find { info ->
            isRouteInfoBoundMatch(companyRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS)
        }

    private fun isRouteBoundMatch(
        companyRoute1: CompanyRoute, companyRoute2: CompanyRoute, errorDistance: Double
    ): Boolean {
        val origin1 = requestedData.stops.find { stop -> stop.stopId == companyRoute1.stops.first() }
        val dest1 = requestedData.stops.find { stop -> stop.stopId == companyRoute1.stops.last() }
        val origin2 = requestedData.stops.find { stop -> stop.stopId == companyRoute2.stops.first() }
        val dest2 = requestedData.stops.find { stop -> stop.stopId == companyRoute2.stops.last() }
        val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1.latLngCoord, origin2.latLngCoord
        ) else Double.MAX_VALUE
        val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
            dest1.latLngCoord, dest2.latLngCoord
        ) else Double.MAX_VALUE
        return originDistance <= errorDistance || destDistance <= errorDistance
    }

    private fun isRouteInfoBoundMatch(
        companyRoute: CompanyRoute, routeInfo: RouteInfo, errorDistance: Double
    ): Boolean {
        val origin1 = requestedData.stops.find { stop -> stop.stopId == companyRoute.stops.first() }
        val dest1 = requestedData.stops.find { stop -> stop.stopId == companyRoute.stops.last() }
        val origin2 = govStops.find { stop -> stop.stopId == routeInfo.stStopId }
        val dest2 = govStops.find { stop -> stop.stopId == routeInfo.edStopId }
        val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1.latLngCoord, origin2.latLngCoord
        ) else Double.MAX_VALUE
        val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
            dest2.latLngCoord, dest2.latLngCoord
        ) else Double.MAX_VALUE
        return originDistance <= errorDistance && destDistance <= errorDistance
    }

    private fun getClosestStopID(stop: Stop, candidateStopIDs: List<String>): String? {
        var result: String? = null
        var minDistance = Double.MAX_VALUE
        candidateStopIDs.forEach {
            val candidateStop = requestedData.stops.find { x -> x.stopId == it }
            if (candidateStop != null) {
                val distance = Utils.distanceInMeters(candidateStop.latLngCoord, stop.latLngCoord)
                if (distance < minDistance) {
                    minDistance = distance
                    result = candidateStop.stopId
                }
            }
        }
        return result
    }

    private fun getStopMap(refRoute: CompanyRoute, matchingRoute: CompanyRoute): Map<String, String> {
        val stopMap = mutableMapOf<String, String>()
        refRoute.stops.forEach { kmbStopId ->
            val refStop = requestedData.stops.find { x -> x.stopId == kmbStopId }
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
            val companies = if (routeInfo != null) {
                routeInfos.remove(routeInfo)
                getCompanies(routeInfo.companyCode)
            } else {
                if (isJointRoute(kmbRoute)) unmappedJointRoutes.add(kmbRoute) else unmappedKmbRoutes.add(kmbRoute)
                setOf(Company.KMB)
            }
            routes.add(
                Route(
                    companies,
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
            val companies = if (routeInfo != null) {
                routeInfos.remove(routeInfo)
                getCompanies(routeInfo.companyCode)
            } else {
                unmappedCtbRoutes.add(it)
                setOf(Company.CTB)
            }
            routes.add(
                Route(
                    companies,
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
            val companies = if (routeInfo != null) {
                routeInfos.remove(routeInfo)
                getCompanies(routeInfo.companyCode)
            } else {
                unmappedNlbRoutes.add(it)
                setOf(Company.NLB)
            }
            routes.add(
                Route(
                    companies,
                    it.number,
                    it.bound,
                    it.originEn,
                    it.originChiT,
                    it.originChiS,
                    it.destEn,
                    it.destChiT,
                    it.destChiS,
                    it.kmbServiceType,
                    it.routeId,
                    routeInfo?.objectId,
                    it.stops,
                    emptyList()
                )
            )
        }
        val kmbRouteCount = routes.filter { isSolelyOfCompany(Company.KMB, it.companies) }.size
        val ctbRouteCount = routes.filter { isSolelyOfCompany(Company.CTB, it.companies) }.size
        val nlbRouteCount = routes.filter { isSolelyOfCompany(Company.NLB, it.companies) }.size
        val jointRouteCount = routes.filter { it.companies.size > 1 }.size
        val mappedKmbRouteCount = kmbRouteCount - unmappedKmbRoutes.size
        val mappedCtbRouteCount = ctbRouteCount - unmappedCtbRoutes.size
        val mappedNlbRouteCount = nlbRouteCount - unmappedNlbRoutes.size

        println("- KMB routes: $kmbRouteCount (mapped: $mappedKmbRouteCount, unmapped: ${unmappedKmbRoutes.size})")
        println("- CTB routes: $ctbRouteCount (mapped: $mappedCtbRouteCount, unmapped: ${unmappedCtbRoutes.size})")
        println("- NLB routes: $nlbRouteCount (mapped: $mappedNlbRouteCount, unmapped: ${unmappedNlbRoutes.size})")
        println("- Joint routes: $jointRouteCount (mapped: $mappedNlbRouteCount, unmapped: ${unmappedJointRoutes.size})")
        val totalUnmapped =
            unmappedKmbRoutes.size + unmappedCtbRoutes.size + unmappedNlbRoutes.size + unmappedJointRoutes.size
        println("- Total routes: ${routes.size} (mapped: ${routes.size - totalUnmapped}, unmapped: $totalUnmapped)")
    }
}

suspend fun runAnalyzer(requestedData: RequestedData) {
    val govStops = mutableListOf<GovStop>()
    val routeInfos = mutableListOf<RouteInfo>()

    print("Loading data...")
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
                govStops.addAll(loadGovRecordStop())
            }
        }
    }
    println("Finished in $t")
    println("Mapped Routes:${routeInfos.size}, Requestable routes:${requestedData.companyRoutes.size}, Stops (on government record):${govStops.size}")

    val analyzer = Analyzer(requestedData, routeInfos, govStops)
    execute("Analyzing...", true) { analyzer.analyze() }

    execute("Rounding LatLng...") {
        val stops = requestedData.stops.map {
            val lat = it.latLngCoord[0].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            val long = it.latLngCoord[1].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            it.copy(latLngCoord = mutableListOf(lat, long))
        }
        requestedData.stops.clear()
        requestedData.stops.addAll(stops)
    }

    execute("Writing routes and stops \"$DB_ROUTES_STOPS_EXPORT_PATH\"...") {
        writeToJsonFile(RSDatabase(analyzer.routes, requestedData.stops).toJson(), DB_ROUTES_STOPS_EXPORT_PATH)
    }

    execute("Writing paths \"$DB_PATHS_EXPORT_PATH\"...", true) {
        val pathIDs = mutableSetOf<Int>()
        analyzer.routes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
        MappedRouteParser.parseFile(
            parseRouteInfo = true, parsePaths = true, pathIDsToWrite = pathIDs, writeSeparatePathFiles = false
        )
    }

    writeToArchive(ARCHIVE_NAME, intermediates, compressToXZ = true, deleteSource = true)
}

suspend fun main() {
    val requestedData = RequestedData()
    execute("Loading saved requested data...") {
        val dbFile = File(REQUESTABLES_EXPORT_PATH)
        val dbStream = GZIPInputStream(dbFile.inputStream())
        val jsonString = dbStream.bufferedReader().use { it.readText() }
        val data = Klaxon().parse<RequestedData>(jsonString)
        if (data != null) {
            requestedData.companyRoutes.addAll(data.companyRoutes)
            requestedData.stops.addAll(data.stops)
        }
    }
    runAnalyzer(requestedData)
}