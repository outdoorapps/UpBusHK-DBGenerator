import com.beust.klaxon.Klaxon
import data.*
import util.Company
import util.Paths
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.DB_PATHS_EXPORT_PATH
import util.Paths.Companion.DB_ROUTES_STOPS_EXPORT_PATH
import util.Paths.Companion.MINIBUS_EXPORT_PATH
import util.Paths.Companion.TRACK_INFO_EXPORT_PATH
import util.TransportType
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.intermediates
import util.Utils.Companion.loadCompanyBusData
import util.Utils.Companion.loadGovStop
import util.Utils.Companion.writeToArchive
import util.Utils.Companion.writeToJsonFile
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

// Matches bus company data and government bus track data
class TrackMatcher(companyBusData: CompanyBusData?, govStops: List<GovStop>?) {
    companion object {
        const val TRACK_INFO_ERROR_DISTANCE_METERS = 150.0
    }

    private val companyBusData: CompanyBusData
    private val govStops: List<GovStop>
    private val trackInfos: List<TrackInfo>

    init {
        lateinit var companyBusDataTemp: CompanyBusData
        lateinit var govStopsTemp: List<GovStop>
        lateinit var trackInfos: List<TrackInfo>

        execute("Initializing data for TrackMatcher...") {
            companyBusDataTemp = companyBusData ?: loadCompanyBusData()
            govStopsTemp = govStops ?: loadGovStop(TransportType.BUS)

            val file = File(TRACK_INFO_EXPORT_PATH)
            file.inputStream().use { input ->
                GZIPInputStream(input).use { gzInput ->
                    val jsonString = gzInput.bufferedReader().use { it.readText() }
                    trackInfos = Klaxon().parseArray<TrackInfo>(jsonString)!!
                }
            }
        }
        this.companyBusData = companyBusDataTemp
        this.govStops = govStopsTemp
        this.trackInfos = trackInfos
    }

    fun matchTracks(busRoutes: List<BusRoute>): List<BusRoute> {
        val mappedRoutes = busRoutes.map { busRoute ->
            busRoute.copy(trackId = getTrackInfo(busRoute)?.objectId)
        }

        val kmbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.KMB) }.size
        val lwbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.LWB) }.size
        val ctbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.CTB) }.size
        val nlbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.NLB) }.size
        val jointRouteCount = busRoutes.filter { it.companies.size > 1 }.size

        val mappedKmbRouteCount =
            mappedRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.KMB) && it.trackId != null }.size
        val mappedLwRouteCount =
            mappedRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.LWB) && it.trackId != null }.size
        val mappedCtbRouteCount =
            mappedRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.CTB) && it.trackId != null }.size
        val mappedNlbRouteCount =
            mappedRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.NLB) && it.trackId != null }.size
        val mappedJointRouteCount = mappedRoutes.filter { it.companies.size > 1 && it.trackId != null }.size

        val unmappedKmbRouteCount = kmbRouteCount - mappedKmbRouteCount
        val unmappedLwRouteCount = lwbRouteCount - mappedLwRouteCount
        val unmappedCtbRouteCount = ctbRouteCount - mappedCtbRouteCount
        val unmappedNlbRouteCount = nlbRouteCount - mappedNlbRouteCount
        val unmappedJointRouteCount = jointRouteCount - mappedJointRouteCount

        val totalMapped = mappedRoutes.filter { it.trackId != null }.size
        val totalUnmapped = mappedRoutes.filter { it.trackId == null }.size

        println("- KMB:$kmbRouteCount (mapped: $mappedKmbRouteCount, unmapped: ${unmappedKmbRouteCount})")
        println("- LWB:$lwbRouteCount (mapped: $mappedLwRouteCount, unmapped: ${unmappedLwRouteCount})")
        println("- CTB:$ctbRouteCount (mapped: $mappedCtbRouteCount, unmapped: ${unmappedCtbRouteCount})")
        println("- NLB:$nlbRouteCount (mapped: $mappedNlbRouteCount, unmapped: ${unmappedNlbRouteCount})")
        println("- Joint:$jointRouteCount (mapped: $mappedJointRouteCount, unmapped: ${unmappedJointRouteCount})")
        println("- Total routes:${busRoutes.size} (mapped: $totalMapped, unmapped: $totalUnmapped)")
        return mappedRoutes
    }

    private fun getTrackInfo(busRoute: BusRoute): TrackInfo? {
        val candidates = trackInfos.filter {
            busRoute.number == it.routeNameE && isCompaniesMatch(
                busRoute.companies, it.companyCode
            ) && isTrackInfoBoundMatch(busRoute, it, TRACK_INFO_ERROR_DISTANCE_METERS)
        }
        return if (candidates.isNotEmpty()) candidates.first() else null
    }

    private fun isCompaniesMatch(companies: Set<Company>, companyCode: String): Boolean {
        val results = companies.map { companyCode.contains(it.value) }
        return !results.contains(false)
    }

    private fun isTrackInfoBoundMatch(busRoute: BusRoute, trackInfo: TrackInfo, errorDistance: Double): Boolean {
        val origin1 = companyBusData.busStops.find { stop -> stop.stopId == busRoute.stopFareMap.keys.first() }
        val destination1 = companyBusData.busStops.find { stop -> stop.stopId == busRoute.stopFareMap.keys.last() }
        val origin2 = govStops.find { stop -> stop.stopId == trackInfo.stStopId }
        val destination2 = govStops.find { stop -> stop.stopId == trackInfo.edStopId }
        val originDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1.coordinate, origin2.coordinate
        ) else Double.MAX_VALUE
        val destinationDistance = if (destination1 != null && destination2 != null) Utils.distanceInMeters(
            destination1.coordinate, destination2.coordinate
        ) else Double.MAX_VALUE
        return originDistance <= errorDistance && destinationDistance <= errorDistance
    }
}

fun main() {
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

    lateinit var minibusData: MinibusData
    execute("Loading saved minibus data...") {
        val dbFile = File(MINIBUS_EXPORT_PATH)
        var jsonString: String
        dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                jsonString = gzInput.bufferedReader().use { it.readText() }
            }
        }
        minibusData = Klaxon().parse<MinibusData>(jsonString)!!
    }
    val govBusDataParser = GovBusDataParser(loadExistingData = true, exportToFile = false)
    val routeMatcher = RouteMatcher(companyBusData, govBusDataParser.govBusData)
    val trackMatcher = TrackMatcher(companyBusData = companyBusData, govStops = null)
    val busRoutes = trackMatcher.matchTracks(routeMatcher.busRoutes)

    val database =
        generateDatabase(busRoutes = busRoutes, busStops = companyBusData.busStops, minibusData = minibusData)

    execute("Writing routes and stops \"$DB_ROUTES_STOPS_EXPORT_PATH\"...") {
        writeToJsonFile(database.toJson(), DB_ROUTES_STOPS_EXPORT_PATH)
    }

    execute("Writing paths \"$DB_PATHS_EXPORT_PATH\"...", true) {
        val pathIDs = mutableSetOf<Int>()
        database.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
        TrackParser.parseFile(
            exportTrackInfoToFile = true, parsePaths = true, pathIDsToWrite = pathIDs, writeSeparatePathFiles = false
        )
    }

    writeToArchive(intermediates, compressToXZ = compressToXZ, deleteSource = true)

    execute("Writing version file \"${Paths.DB_VERSION_EXPORT_PATH}\"...") {
        val out = FileOutputStream(Paths.DB_VERSION_EXPORT_PATH)
        out.use {
            it.write(database.version.toByteArray())
        }
    }
}