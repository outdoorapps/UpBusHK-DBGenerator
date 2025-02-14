import Main.Companion.generateDatabase
import RouteMerger.Companion.CIRCULAR_ROUTE_ERROR_DISTANCE_METERS
import com.beust.klaxon.Klaxon
import data.*
import util.Company
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.DB_PATHS_EXPORT_PATH
import util.Paths.Companion.DB_ROUTES_STOPS_EXPORT_PATH
import util.Paths.Companion.MINIBUS_DATA_EXPORT_PATH
import util.Paths.Companion.TRACK_INFO_EXPORT_PATH
import util.Utils.Companion.distanceInMeters
import util.Utils.Companion.execute
import util.Utils.Companion.intermediates
import util.Utils.Companion.loadCompanyBusData
import util.Utils.Companion.loadGovBusStopsCoordinatesOnly
import util.Utils.Companion.writeToArchive
import util.Utils.Companion.writeToJsonFile
import java.io.File
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
            govStopsTemp = govStops ?: loadGovBusStopsCoordinatesOnly()

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
        val mappedRoutes = busRoutes.map { busRoute -> busRoute.copy(trackId = getTrackInfo(busRoute)?.objectId) }

        val kmbRouteCount = busRoutes.count { it.companies.size == 1 && it.companies.contains(Company.KMB) }
        val lwbRouteCount = busRoutes.count { it.companies.size == 1 && it.companies.contains(Company.LWB) }
        val ctbRouteCount = busRoutes.count { it.companies.size == 1 && it.companies.contains(Company.CTB) }
        val nlbRouteCount = busRoutes.count { it.companies.size == 1 && it.companies.contains(Company.NLB) }
        val mtrbRouteCount = busRoutes.count { it.companies.size == 1 && it.companies.contains(Company.MTRB) }
        val jointRouteCount = busRoutes.count { it.companies.size > 1 }

        val mappedKmbRouteCount =
            mappedRoutes.count { it.companies.size == 1 && it.companies.contains(Company.KMB) && it.trackId != null }
        val mappedLwRouteCount =
            mappedRoutes.count { it.companies.size == 1 && it.companies.contains(Company.LWB) && it.trackId != null }
        val mappedCtbRouteCount =
            mappedRoutes.count { it.companies.size == 1 && it.companies.contains(Company.CTB) && it.trackId != null }
        val mappedNlbRouteCount =
            mappedRoutes.count { it.companies.size == 1 && it.companies.contains(Company.NLB) && it.trackId != null }
        val mappedMtrbRouteCount =
            mappedRoutes.count { it.companies.size == 1 && it.companies.contains(Company.MTRB) && it.trackId != null }
        val mappedJointRouteCount = mappedRoutes.count { it.companies.size > 1 && it.trackId != null }

        val unmappedKmbRouteCount = kmbRouteCount - mappedKmbRouteCount
        val unmappedLwRouteCount = lwbRouteCount - mappedLwRouteCount
        val unmappedCtbRouteCount = ctbRouteCount - mappedCtbRouteCount
        val unmappedNlbRouteCount = nlbRouteCount - mappedNlbRouteCount
        val unmappedMtrbRouteCount = mtrbRouteCount - mappedMtrbRouteCount
        val unmappedJointRouteCount = jointRouteCount - mappedJointRouteCount

        val totalMapped = mappedRoutes.count { it.trackId != null }
        val totalUnmapped = mappedRoutes.count { it.trackId == null }

        println("- KMB:$kmbRouteCount (mapped: $mappedKmbRouteCount, unmapped: $unmappedKmbRouteCount)")
        println("- LWB:$lwbRouteCount (mapped: $mappedLwRouteCount, unmapped: $unmappedLwRouteCount)")
        println("- CTB:$ctbRouteCount (mapped: $mappedCtbRouteCount, unmapped: $unmappedCtbRouteCount)")
        println("- NLB:$nlbRouteCount (mapped: $mappedNlbRouteCount, unmapped: $unmappedNlbRouteCount)")
        println("- MTRB:$mtrbRouteCount (mapped: $mappedMtrbRouteCount, unmapped: $unmappedMtrbRouteCount)")
        println("- Joint:$jointRouteCount (mapped: $mappedJointRouteCount, unmapped: $unmappedJointRouteCount)")
        println("- Total routes:${busRoutes.size} (mapped: $totalMapped, unmapped: $totalUnmapped)")
        return mappedRoutes
    }

    private fun getTrackInfo(busRoute: BusRoute): TrackInfo? {
        return if (busRoute.govRouteId != null && busRoute.govRouteSeq != null) {
            trackInfos.find { it.routeId == busRoute.govRouteId && it.routeSeq == busRoute.govRouteSeq }
        } else {
            trackInfos.find {
                busRoute.number == it.routeNameE && isCompaniesMatch(
                    busRoute.companies, it.companyCode
                ) && isTrackInfoBoundMatch(
                    busRoute, it, TRACK_INFO_ERROR_DISTANCE_METERS, CIRCULAR_ROUTE_ERROR_DISTANCE_METERS
                )
            }
        }
    }

    private fun isCompaniesMatch(companies: Set<Company>, companyCode: String): Boolean {
        val results = companies.map { companyCode.contains(it.name) }
        return !results.contains(false)
    }

    private fun isTrackInfoBoundMatch(
        busRoute: BusRoute, trackInfo: TrackInfo, errorDistance: Double, circularRouteErrorDistance: Double
    ): Boolean {
        val comOrigin = companyBusData.busStops.find { stop -> stop.stopId == busRoute.stopFarePairs.first().first }
        val comDestination = companyBusData.busStops.find { stop -> stop.stopId == busRoute.stopFarePairs.last().first }
        val govOrigin = govStops.find { stop -> stop.stopId == trackInfo.stStopId }
        val govDestination = govStops.find { stop -> stop.stopId == trackInfo.edStopId }
        val originDistance = if (comOrigin != null && govOrigin != null) distanceInMeters(
            comOrigin.coordinate, govOrigin.coordinate
        ) else Double.MAX_VALUE
        val destinationDistance = if (comDestination != null && govDestination != null) distanceInMeters(
            comDestination.coordinate, govDestination.coordinate
        ) else Double.MAX_VALUE
        val govOriginComDestinationDistance =
            if (trackInfo.stStopNameE == trackInfo.edStopNameE && govOrigin != null && comDestination != null) {
                distanceInMeters(govOrigin.coordinate, comDestination.coordinate)
            } else Double.MAX_VALUE

        return (originDistance <= errorDistance && destinationDistance <= errorDistance) || govOriginComDestinationDistance < circularRouteErrorDistance
    }
}

fun main() {
    val companyBusData = CompanyBusData()
    execute("Loading saved bus company data...") {
        val dbFile = File(BUS_COMPANY_DATA_EXPORT_PATH)
        val jsonString = dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                gzInput.bufferedReader().use { it.readText() }
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
        val dbFile = File(MINIBUS_DATA_EXPORT_PATH)
        var jsonString: String
        dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                jsonString = gzInput.bufferedReader().use { it.readText() }
            }
        }
        minibusData = Klaxon().parse<MinibusData>(jsonString)!!
    }
    val govBusData = GovDataParser.getGovBusData(loadExistingData = true, exportToFile = false)
    val routeMerger = RouteMerger(companyBusData, govBusData)
    val trackMatcher = TrackMatcher(companyBusData = companyBusData, govStops = null)
    val busRoutes = trackMatcher.matchTracks(routeMerger.busRoutes)
    val database = generateDatabase(busRoutes = busRoutes, busStops = routeMerger.busStops, minibusData = minibusData)

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

    writeToArchive(
        files = intermediates,
        version = database.version,
        compressToXZ = compressToXZ,
        deleteSource = true,
        cleanUpPreviousVersion = false
    )
}