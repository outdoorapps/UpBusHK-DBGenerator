import TrackMatcher.Companion.intermediates
import com.beust.klaxon.Klaxon
import data.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import util.Company
import util.Paths
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.DB_PATHS_EXPORT_PATH
import util.Paths.Companion.DB_ROUTES_STOPS_EXPORT_PATH
import util.Paths.Companion.TRACK_INFO_EXPORT_PATH
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.getCompanies
import util.Utils.Companion.isSolelyOfCompany
import util.Utils.Companion.loadGovRecordStop
import util.Utils.Companion.roundCoordinate
import util.Utils.Companion.writeToArchive
import util.Utils.Companion.writeToJsonFile
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime

// Matches bus company data and government bus track data
class TrackMatcher(
    private val companyBusData: CompanyBusData,
    private val trackInfos: MutableList<TrackInfo>,
    private val govStops: MutableList<GovStop>
) {
    companion object {
        const val TRACK_INFO_ERROR_DISTANCE_METERS = 150.0
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0
        val intermediates = listOf(DB_ROUTES_STOPS_EXPORT_PATH, DB_PATHS_EXPORT_PATH)
    }

    // LWB BusCompanyRoutes are labeled as KMB routes
    private val kmbLwbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.KMB }
    private val ctbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.CTB }
    private val nlbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.NLB }
    private val lwbRouteNumbers: Set<String>
    private val jointRouteNumbers = mutableSetOf<String>()
    val busRoutes = mutableListOf<BusRoute>()

    // For stats
    private val unmappedKmbRoutes = mutableListOf<CompanyBusRoute>()
    private val unmappedLwbRoutes = mutableListOf<CompanyBusRoute>()
    private val unmappedCtbRoutes = mutableListOf<CompanyBusRoute>()
    private val unmappedNlbRoutes = mutableListOf<CompanyBusRoute>()
    private val unmappedJointRoutes = mutableListOf<CompanyBusRoute>()
    private val jointRoutes = mutableListOf<CompanyBusRoute>()

    init {
        val lwbRoutesNumbersSet = mutableSetOf<String>()
        var lwbRouteCount = 0
        trackInfos.forEach {
            if (it.companyCode.contains("+")) {
                jointRouteNumbers.add(it.routeNameE)
            }
            if (it.companyCode.contains(Company.LWB.value)) {
                lwbRoutesNumbersSet.add(it.routeNameE)
                if (!it.companyCode.contains("+")) lwbRouteCount++
            }
        }
        lwbRouteNumbers = lwbRoutesNumbersSet
        val kmb = kmbLwbBusCompanyRoutes.size - lwbRouteCount
        println(
            "KMB:${kmb}, LWB:${lwbRouteCount}, CTB:${ctbBusCompanyRoutes.size}, NLB:${nlbBusCompanyRoutes.size}, Joint (unique route number):${jointRouteNumbers.size}"
        )
    }

    private fun isJointRoute(companyBusRoute: CompanyBusRoute): Boolean =
        jointRouteNumbers.contains(companyBusRoute.number)

    private fun getTrackInfoCandidates(companyBusRoute: CompanyBusRoute): List<TrackInfo> = trackInfos.filter { info ->
        when (companyBusRoute.company) {
            Company.KMB, Company.LWB -> (info.companyCode.contains(companyBusRoute.company.value) || info.companyCode.contains(
                Company.LWB.value
            )) && info.routeNameE == companyBusRoute.number

            Company.CTB, Company.NLB -> info.companyCode.contains(companyBusRoute.company.value) && info.routeNameE == companyBusRoute.number
            Company.MTRB -> TODO()
        }
    }

    private fun getTrackInfo(companyBusRoute: CompanyBusRoute): TrackInfo? =
        getTrackInfoCandidates(companyBusRoute).find { info ->
            isTrackInfoBoundMatch(companyBusRoute, info, TRACK_INFO_ERROR_DISTANCE_METERS)
        }

    private fun isRouteBoundMatch(
        companyBusRoute1: CompanyBusRoute, companyBusRoute2: CompanyBusRoute, errorDistance: Double
    ): Boolean {
        val origin1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute1.stops.first() }
        val dest1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute1.stops.last() }
        val origin2 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute2.stops.first() }
        val dest2 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute2.stops.last() }
        val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1.coordinate, origin2.coordinate
        ) else Double.MAX_VALUE
        val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
            dest1.coordinate, dest2.coordinate
        ) else Double.MAX_VALUE
        return originDistance <= errorDistance || destDistance <= errorDistance
    }

    private fun isTrackInfoBoundMatch(
        companyBusRoute: CompanyBusRoute, trackInfo: TrackInfo, errorDistance: Double
    ): Boolean {
        val origin1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.first() }
        val dest1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.last() }
        val origin2 = govStops.find { stop -> stop.stopId == trackInfo.stStopId }
        val dest2 = govStops.find { stop -> stop.stopId == trackInfo.edStopId }
        val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1.coordinate, origin2.coordinate
        ) else Double.MAX_VALUE
        val destDistance = if (dest1 != null && dest2 != null) Utils.distanceInMeters(
            dest1.coordinate, dest2.coordinate
        ) else Double.MAX_VALUE
        return originDistance <= errorDistance && destDistance <= errorDistance
    }

    private fun getClosestStopID(busStop: BusStop, candidateStopIDs: List<String>): String? {
        var result: String? = null
        var minDistance = Double.MAX_VALUE
        candidateStopIDs.forEach {
            val candidateStop = companyBusData.busStops.find { x -> x.stopId == it }
            if (candidateStop != null) {
                val distance = Utils.distanceInMeters(candidateStop.coordinate, busStop.coordinate)
                if (distance < minDistance) {
                    minDistance = distance
                    result = candidateStop.stopId
                }
            }
        }
        return result
    }

    private fun getStopMap(refRoute: CompanyBusRoute, matchingRoute: CompanyBusRoute): List<String> {
        val secondaryStops = mutableListOf<String>()
        refRoute.stops.forEach { refStopId ->
            val refStop = companyBusData.busStops.find { x -> x.stopId == refStopId }
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

    // todo analyze and matchTracks ambiguous
    fun analyze() {
        // Match TrackInfo
        kmbLwbBusCompanyRoutes.forEach { kmbLwbRoute ->
            // Merge KMB/LWB and CTB routes
            val trackInfo: TrackInfo? = getTrackInfo(kmbLwbRoute)
            val secondaryStops = mutableListOf<String>()
            if (isJointRoute(kmbLwbRoute)) {
                val ctbRoute = ctbBusCompanyRoutes.find { x ->
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
            // Get the correct company types, populate unmapped route, remove matching trackInfo to avoid duplicating match
            val company = if (lwbRouteNumbers.contains(kmbLwbRoute.number)) Company.LWB else Company.KMB
            val companies = if (trackInfo != null) {
                trackInfos.remove(trackInfo)
                getCompanies(trackInfo.companyCode)
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
                    trackInfo?.objectId,
                    null,
                    kmbLwbRoute.stops,
                    secondaryStops
                )
            )
        }

        ctbBusCompanyRoutes.filter { !isJointRoute(it) }.forEach {
            val trackInfo: TrackInfo? = getTrackInfo(it)
            val companies = if (trackInfo != null) {
                trackInfos.remove(trackInfo)
                getCompanies(trackInfo.companyCode)
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
                    trackInfo?.objectId,
                    null,
                    it.stops,
                    emptyList(),
                )
            )
        }

        nlbBusCompanyRoutes.forEach {
            val trackInfo: TrackInfo? = getTrackInfo(it)
            val companies = if (trackInfo != null) {
                trackInfos.remove(trackInfo)
                getCompanies(trackInfo.companyCode)
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
                    trackInfo?.objectId,
                    null,
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
        val mappedJointRouteCount = jointRouteCount - unmappedJointRoutes.size

        println("- KMB routes: $kmbRouteCount (mapped: $mappedKmbRouteCount, unmapped: ${unmappedKmbRoutes.size})")
        println("- LWB routes: $lwbRouteCount (mapped: $mappedLwRouteCount, unmapped: ${unmappedLwbRoutes.size})")
        println("- CTB routes: $ctbRouteCount (mapped: $mappedCtbRouteCount, unmapped: ${unmappedCtbRoutes.size})")
        println("- NLB routes: $nlbRouteCount (mapped: $mappedNlbRouteCount, unmapped: ${unmappedNlbRoutes.size})")
        println("- Joint routes: $jointRouteCount (mapped: $mappedJointRouteCount, unmapped: ${unmappedJointRoutes.size})")
        val totalUnmapped =
            unmappedKmbRoutes.size + unmappedCtbRoutes.size + unmappedNlbRoutes.size + unmappedJointRoutes.size
        println("- Total routes: ${busRoutes.size} (mapped: ${busRoutes.size - totalUnmapped}, unmapped: $totalUnmapped)")
    }
}

suspend fun matchTracks(companyBusData: CompanyBusData): RoutesStopsDatabase {

    val govStops = mutableListOf<GovStop>()
    val trackInfos = mutableListOf<TrackInfo>()

    print("Loading data...")
    val t = measureTime {
        coroutineScope {
            launch {
                val file = File(TRACK_INFO_EXPORT_PATH)
                file.inputStream().use { input ->
                    GZIPInputStream(input).use { gzInput ->
                        val jsonString = gzInput.bufferedReader().use { it.readText() }
                        trackInfos.addAll(Klaxon().parseArray<TrackInfo>(jsonString)!!.toList())
                    }
                }
            }
            launch {
                govStops.addAll(loadGovRecordStop())
            }
        }
    }
    println("Finished in $t")
    println("Mapped Routes:${trackInfos.size}, Bus company routes:${companyBusData.companyBusRoutes.size}, Stops (on government record):${govStops.size}")

    val trackMatcher = TrackMatcher(companyBusData, trackInfos, govStops)

    execute("Analyzing...", true) { trackMatcher.analyze() }

    execute("Rounding coordinate...") {
        val stops = companyBusData.busStops.map {
            val lat = it.coordinate[0].roundCoordinate()
            val long = it.coordinate[1].roundCoordinate()
            it.copy(coordinate = mutableListOf(lat, long))
        }
        companyBusData.busStops.clear()
        companyBusData.busStops.addAll(stops)
    }

    val version = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    return RoutesStopsDatabase(
        version.toString(), trackMatcher.busRoutes, companyBusData.busStops, emptyList(), emptySet()
    )
}

suspend fun main() {
    val companyBusData = CompanyBusData()
    execute("Loading saved bus company data...") {
        val dbFile = File(BUS_COMPANY_DATA_EXPORT_PATH)
        var jsonString: String
        dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                jsonString = gzInput.bufferedReader().use { it.readText() }
            }
        }
        val data = Klaxon().parse<CompanyBusData>(jsonString)
        if (data != null) {
            companyBusData.companyBusRoutes.addAll(data.companyBusRoutes)
            companyBusData.busStops.addAll(data.busStops)
        }
    }
    val rsDatabase = matchTracks(companyBusData)

    execute("Writing routes and stops \"$DB_ROUTES_STOPS_EXPORT_PATH\"...") {
        writeToJsonFile(rsDatabase.toJson(), DB_ROUTES_STOPS_EXPORT_PATH)
    }

    execute("Writing paths \"$DB_PATHS_EXPORT_PATH\"...", true) {
        val pathIDs = mutableSetOf<Int>()
        rsDatabase.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
        MappedRouteParser.parseFile(
            exportTrackInfoToFile = true, parsePaths = true, pathIDsToWrite = pathIDs, writeSeparatePathFiles = false
        )
    }

    writeToArchive(intermediates, compressToXZ = compressToXZ, deleteSource = true)

    execute("Writing version file \"${Paths.DB_VERSION_EXPORT_PATH}\"...") {
        val out = FileOutputStream(Paths.DB_VERSION_EXPORT_PATH)
        out.use {
            it.write(rsDatabase.version.toByteArray())
        }
    }
}