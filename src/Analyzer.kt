import Analyzer.Companion.intermediates
import com.beust.klaxon.Klaxon
import data.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import utils.Company
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
import java.time.LocalDateTime
import java.time.ZoneOffset
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

    // LWB RequestableRoutes are labeled as KMB routes
    private val kmbLwbRequestableRoutes = requestedData.companyRoutes.filter { it.company == Company.KMB }
    private val ctbRequestableRoutes = requestedData.companyRoutes.filter { it.company == Company.CTB }
    private val nlbRequestableRoutes = requestedData.companyRoutes.filter { it.company == Company.NLB }
    private val lwbRouteNumbers: Set<String>
    private val jointRouteNumbers = mutableSetOf<String>()
    val busRoutes = mutableListOf<BusRoute>()

    // For stats
    private val unmappedKmbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedLwbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedCtbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedNlbRoutes = mutableListOf<CompanyRoute>()
    private val unmappedJointRoutes = mutableListOf<CompanyRoute>()
    private val jointRoutes = mutableListOf<CompanyRoute>()

    init {
        val lwbRoutesNumbersSet = mutableSetOf<String>()
        var lwbRouteCount = 0
        routeInfos.forEach {
            if (it.companyCode.contains("+")) {
                jointRouteNumbers.add(it.routeNameE)
            }
            if (it.companyCode.contains(Company.LWB.value)) {
                lwbRoutesNumbersSet.add(it.routeNameE)
                if (!it.companyCode.contains("+")) lwbRouteCount++
            }
        }
        lwbRouteNumbers = lwbRoutesNumbersSet
        val kmb = kmbLwbRequestableRoutes.size - lwbRouteCount
        println(
            "KMB:${kmb}, LWB:${lwbRouteCount}, CTB:${ctbRequestableRoutes.size}, NLB:${nlbRequestableRoutes.size}, Joint (unique route number):${jointRouteNumbers.size}"
        )
    }

    private fun isJointRoute(companyRoute: CompanyRoute): Boolean = jointRouteNumbers.contains(companyRoute.number)

    private fun getRouteInfoCandidates(companyRoute: CompanyRoute): List<RouteInfo> = routeInfos.filter { info ->
        when (companyRoute.company) {
            Company.KMB, Company.LWB -> (info.companyCode.contains(companyRoute.company.value) || info.companyCode.contains(
                Company.LWB.value
            )) && info.routeNameE == companyRoute.number

            Company.CTB, Company.NLB -> info.companyCode.contains(companyRoute.company.value) && info.routeNameE == companyRoute.number
            Company.MTRB -> TODO()
        }
    }

    private fun getRouteInfo(companyRoute: CompanyRoute): RouteInfo? =
        getRouteInfoCandidates(companyRoute).find { info ->
            isRouteInfoBoundMatch(
                companyRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS
            )
        }


    private fun isRouteBoundMatch(
        companyRoute1: CompanyRoute, companyRoute2: CompanyRoute, errorDistance: Double
    ): Boolean {
        val origin1 = requestedData.busStops.find { stop -> stop.stopId == companyRoute1.stops.first() }
        val dest1 = requestedData.busStops.find { stop -> stop.stopId == companyRoute1.stops.last() }
        val origin2 = requestedData.busStops.find { stop -> stop.stopId == companyRoute2.stops.first() }
        val dest2 = requestedData.busStops.find { stop -> stop.stopId == companyRoute2.stops.last() }
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
        val origin1 = requestedData.busStops.find { stop -> stop.stopId == companyRoute.stops.first() }
        val dest1 = requestedData.busStops.find { stop -> stop.stopId == companyRoute.stops.last() }
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

    private fun getClosestStopID(busStop: BusStop, candidateStopIDs: List<String>): String? {
        var result: String? = null
        var minDistance = Double.MAX_VALUE
        candidateStopIDs.forEach {
            val candidateStop = requestedData.busStops.find { x -> x.stopId == it }
            if (candidateStop != null) {
                val distance = Utils.distanceInMeters(candidateStop.latLngCoord, busStop.latLngCoord)
                if (distance < minDistance) {
                    minDistance = distance
                    result = candidateStop.stopId
                }
            }
        }
        return result
    }

    private fun getStopMap(refRoute: CompanyRoute, matchingRoute: CompanyRoute): List<String> {
        val secondaryStops = mutableListOf<String>()
        refRoute.stops.forEach { refStopId ->
            val refStop = requestedData.busStops.find { x -> x.stopId == refStopId }
            // Search a sublist of remaining stops
            val startIndex = if (secondaryStops.isEmpty()) {
                0
            } else {
                val index = matchingRoute.stops.indexOf(secondaryStops.last())
                if (index == matchingRoute.stops.size) index else index + 1
            }

            val matchingStopId = if (refStop != null) {
                getClosestStopID(refStop, matchingRoute.stops.subList(startIndex, matchingRoute.stops.size))
            } else null
            secondaryStops.add(matchingStopId ?: "")
        }
        if (secondaryStops.contains("")) {
            for (i in secondaryStops.indices) {
                if (secondaryStops[i].isEmpty()) {
                    println("Not match for StopID:${refRoute.stops[i]}, (${refRoute.number},${refRoute.bound},${refRoute.kmbServiceType})")
                }
            }
        }
        return secondaryStops
    }

    fun analyze() {
        // Match routeInfo
        kmbLwbRequestableRoutes.forEach { kmbLwbRoute ->
            // Merge KMB/LWB and CTB routes
            val routeInfo: RouteInfo? = getRouteInfo(kmbLwbRoute)
            val secondaryStops = mutableListOf<String>()
            if (isJointRoute(kmbLwbRoute)) {
                val ctbRoute = ctbRequestableRoutes.find { x ->
                    x.number == kmbLwbRoute.number && isRouteBoundMatch(
                        x, kmbLwbRoute, JOINT_ROUTE_ERROR_DISTANCE_METERS
                    )
                }
                if (ctbRoute == null) {
                    println(
                        "No CTB route matches KMB/LWB route: ${kmbLwbRoute.number},Bound:${kmbLwbRoute.bound}, service type:${kmbLwbRoute.kmbServiceType}"
                    )
                } else {
                    secondaryStops.addAll(getStopMap(kmbLwbRoute, ctbRoute))
                    jointRoutes.add(kmbLwbRoute)
                }
            }
            val company = if (lwbRouteNumbers.contains(kmbLwbRoute.number)) Company.LWB else Company.KMB
            val companies = if (routeInfo != null) {
                routeInfos.remove(routeInfo)
                getCompanies(routeInfo.companyCode)
            } else {
                if (isJointRoute(kmbLwbRoute)) {
                    unmappedJointRoutes.add(kmbLwbRoute)
                    setOf(company, Company.CTB)
                } else {
                    if (company == Company.KMB) unmappedKmbRoutes.add(kmbLwbRoute)
                    else unmappedLwbRoutes.add(kmbLwbRoute)
                    setOf(company)
                }
            }
            if (secondaryStops.isNotEmpty() && kmbLwbRoute.stops.size != secondaryStops.size) {
                println(
                    "Primary-secondary stops size not equal (${kmbLwbRoute.stops.size}&${secondaryStops.size}): ${kmbLwbRoute.number},${kmbLwbRoute.bound},${kmbLwbRoute.kmbServiceType}"
                )
            }
            busRoutes.add(
                BusRoute(
                    companies,
                    kmbLwbRoute.number,
                    kmbLwbRoute.bound,
                    kmbLwbRoute.originEn,
                    kmbLwbRoute.originChiT,
                    kmbLwbRoute.originChiS,
                    kmbLwbRoute.destEn,
                    kmbLwbRoute.destChiT,
                    kmbLwbRoute.destChiS,
                    kmbLwbRoute.kmbServiceType,
                    null,
                    routeInfo?.objectId,
                    kmbLwbRoute.stops,
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
            busRoutes.add(
                BusRoute(
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
            busRoutes.add(
                BusRoute(
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
        val kmbRouteCount = busRoutes.filter { isSolelyOfCompany(Company.KMB, it.companies) }.size
        val lwbRouteCount = busRoutes.filter { isSolelyOfCompany(Company.LWB, it.companies) }.size
        val ctbRouteCount = busRoutes.filter { isSolelyOfCompany(Company.CTB, it.companies) }.size
        val nlbRouteCount = busRoutes.filter { isSolelyOfCompany(Company.NLB, it.companies) }.size
        val jointRouteCount = busRoutes.filter { it.companies.size > 1 }.size
        val mappedKmbRouteCount = kmbRouteCount - unmappedKmbRoutes.size
        val mappedLwRouteCount = lwbRouteCount - unmappedLwbRoutes.size
        val mappedCtbRouteCount = ctbRouteCount - unmappedCtbRoutes.size
        val mappedNlbRouteCount = nlbRouteCount - unmappedNlbRoutes.size

        println("- KMB routes: $kmbRouteCount (mapped: $mappedKmbRouteCount, unmapped: ${unmappedKmbRoutes.size})")
        println("- LWB routes: $lwbRouteCount (mapped: $mappedLwRouteCount, unmapped: ${unmappedLwbRoutes.size})")
        println("- CTB routes: $ctbRouteCount (mapped: $mappedCtbRouteCount, unmapped: ${unmappedCtbRoutes.size})")
        println("- NLB routes: $nlbRouteCount (mapped: $mappedNlbRouteCount, unmapped: ${unmappedNlbRoutes.size})")
        println("- Joint routes: $jointRouteCount (mapped: $mappedNlbRouteCount, unmapped: ${unmappedJointRoutes.size})")
        val totalUnmapped =
            unmappedKmbRoutes.size + unmappedCtbRoutes.size + unmappedNlbRoutes.size + unmappedJointRoutes.size
        println("- Total routes: ${busRoutes.size} (mapped: ${busRoutes.size - totalUnmapped}, unmapped: $totalUnmapped)")
    }
}

suspend fun runAnalyzer(requestedData: RequestedData): LocalDateTime {

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
        val stops = requestedData.busStops.map {
            val lat = it.latLngCoord[0].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            val long = it.latLngCoord[1].toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            it.copy(latLngCoord = mutableListOf(lat, long))
        }
        requestedData.busStops.clear()
        requestedData.busStops.addAll(stops)
    }

    val version = LocalDateTime.now(ZoneOffset.UTC)
    execute("Writing routes and stops \"$DB_ROUTES_STOPS_EXPORT_PATH\"...") {
        writeToJsonFile(
            RSDatabase(version.toString(), analyzer.busRoutes, requestedData.busStops).toJson(),
            DB_ROUTES_STOPS_EXPORT_PATH
        )
    }

    execute("Writing paths \"$DB_PATHS_EXPORT_PATH\"...", true) {
        val pathIDs = mutableSetOf<Int>()
        analyzer.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
        MappedRouteParser.parseFile(
            parseRouteInfo = true, parsePaths = true, pathIDsToWrite = pathIDs, writeSeparatePathFiles = false
        )
    }

    writeToArchive(intermediates, compressToXZ = compressToXZ, deleteSource = true)
    return version
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
            requestedData.busStops.addAll(data.busStops)
        }
    }
    runAnalyzer(requestedData)
}