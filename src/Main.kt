import Main.Companion.dirs
import Main.Companion.generateDatabase
import Main.Companion.getBusCompanyData
import Uploader.Companion.upload
import data.*
import helper.BusRouteHelper.Companion.getRoutes
import helper.BusStopHelper
import helper.MinibusHelper
import org.apache.log4j.BasicConfigurator
import util.Company
import util.HttpUtils.Companion.downloadIgnoreCertificate
import util.Patch.Companion.patchRoutes
import util.Patch.Companion.patchStops
import util.Paths
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.BUS_FARE_PATH
import util.Paths.Companion.BUS_FARE_URL
import util.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import util.Paths.Companion.BUS_ROUTES_GEOJSON_URL
import util.Paths.Companion.BUS_ROUTE_STOP_JSON_PATH
import util.Paths.Companion.BUS_ROUTE_STOP_URL
import util.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import util.Paths.Companion.BUS_STOPS_GEOJSON_URL
import util.Paths.Companion.MINIBUS_ROUTES_GEOJSON_URL
import util.Paths.Companion.MINIBUS_ROUTES_JSON_PATH
import util.Paths.Companion.debugDir
import util.Paths.Companion.generatedDir
import util.Paths.Companion.govDataDir
import util.Paths.Companion.resourcesDir
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.executeWithCount
import util.Utils.Companion.generateVersionNumber
import util.Utils.Companion.getDatabaseFile
import util.Utils.Companion.intermediates
import util.Utils.Companion.roundCoordinate
import util.Utils.Companion.writeToGZ
import java.io.File
import kotlin.time.measureTime

const val compressToXZ = true
const val dbMinAppVersion = "1.2.0" // *Updated every time when there are breaking changes

fun main() {
    BasicConfigurator.configure()
    dirs.forEach {
        val dir = File(it)
        if (!dir.exists()) dir.mkdir()
    }

    val t = measureTime {
        // I. Download Government data files
//        downloadIgnoreCertificate(BUS_ROUTES_GEOJSON_URL, BUS_ROUTES_GEOJSON_PATH)
//        downloadIgnoreCertificate(BUS_STOPS_GEOJSON_URL, BUS_STOPS_GEOJSON_PATH)
//        downloadIgnoreCertificate(BUS_ROUTE_STOP_URL, BUS_ROUTE_STOP_JSON_PATH)
//        downloadIgnoreCertificate(BUS_FARE_URL, BUS_FARE_PATH)
//        downloadIgnoreCertificate(MINIBUS_ROUTES_GEOJSON_URL, MINIBUS_ROUTES_JSON_PATH)

        // II. Build bus (company) and minibus (online and government) data
        val companyBusData = getBusCompanyData()
        val minibusData = MinibusHelper().getMinibusData(exportToFile = true, exportIntermediates = true)

        // III. Parse government bus data
        val govBusData = GovDataParser.getGovBusData(loadExistingData = false, exportToFile = true)

        // IV. Match company routes with government record
        val routeMerger = RouteMerger(companyBusData, govBusData)

        // V. Match company routes with government tracks record
        execute("Parsing trackInfo...", true) {
            TrackParser.parseFile(
                exportTrackInfoToFile = true, parsePaths = false, pathIDsToWrite = null, writeSeparatePathFiles = false
            )
        }

        val trackMatcher = TrackMatcher(companyBusData = companyBusData, govStops = null)
        val busRoutes = trackMatcher.matchTracks(routeMerger.busRoutes)

        // VI. Generate database
        val database =
            generateDatabase(busRoutes = busRoutes, busStops = routeMerger.busStops, minibusData = minibusData)

        // VII. Write to archive
        execute("Writing routes and stops \"${Paths.DB_ROUTES_STOPS_EXPORT_PATH}\"...") {
            Utils.writeToJsonFile(database.toJson(), Paths.DB_ROUTES_STOPS_EXPORT_PATH)
        }

        execute("Writing tracks \"${Paths.DB_PATHS_EXPORT_PATH}\"...", true) {
            val pathIDs = mutableSetOf<Int>()
            database.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
            TrackParser.parseFile(
                exportTrackInfoToFile = true,
                parsePaths = true,
                pathIDsToWrite = pathIDs,
                writeSeparatePathFiles = false
            )
        }

        Utils.writeToArchive(
            files = intermediates,
            version = database.version,
            compressToXZ = compressToXZ,
            deleteSource = true,
            cleanUpPreviousVersion = true
        )

        // VI. Upload to Firebase and marked changes
//        val dbFile = getDatabaseFile()
//        if (dbFile != null) {
//            upload(dbFile, database.version)
//        } else {
//            println("Database file not found, nothing is uploaded")
//        }
    }
    println("Finished all tasks in $t")
}

class Main {
    companion object {
        val dirs = listOf(resourcesDir, govDataDir, generatedDir, debugDir)
        // private val logger: Logger = LoggerFactory.getLogger(OkHttpUtil::class.java.name)

        fun getBusCompanyData(): CompanyBusData {
            val companyBusData = CompanyBusData()
            // 1. Get Routes
            executeWithCount("Getting KMB routes...") {
                val routes = getRoutes(Company.KMB)
                companyBusData.companyBusRoutes.addAll(routes)
                routes.size
            }
            executeWithCount("Getting CTB routes...") {
                val routes = getRoutes(Company.CTB)
                companyBusData.companyBusRoutes.addAll(routes)
                routes.size
            }
            executeWithCount("Getting NLB routes...") {
                val routes = getRoutes(Company.NLB)
                companyBusData.companyBusRoutes.addAll(routes)
                routes.size
            }
            executeWithCount("Parsing MTRB routes...") {
                val routes = getRoutes(Company.MTRB)
                companyBusData.companyBusRoutes.addAll(routes)
                routes.size
            }

            // 2. Get Stops
            val busStopHelper = BusStopHelper()
            executeWithCount("Getting KMB stops...") {
                val stops = busStopHelper.getKmbStops()
                companyBusData.busStops.addAll(stops)
                stops.size
            }
            executeWithCount("Getting CTB stops...") {
                val stops = busStopHelper.getCtbStops(companyBusData.companyBusRoutes)
                companyBusData.busStops.addAll(stops)
                stops.size
            }
            executeWithCount("Getting NLB stops...") {
                val stops = busStopHelper.getNlbStops(companyBusData.companyBusRoutes)
                companyBusData.busStops.addAll(stops)
                stops.size
            }
            executeWithCount("Parsing MTRB stops...") {
                val stops = busStopHelper.getMtrbStops()
                companyBusData.busStops.addAll(stops)
                stops.size
            }
            busStopHelper.validateStops(companyBusData)

            // 3. Patch bus company data
            execute("Patching bus company data...") {
                patchRoutes(companyBusData.companyBusRoutes)
                patchStops(companyBusData.busStops)
            }

            // 4. Write bus company data
            execute("Writing bus company data \"$BUS_COMPANY_DATA_EXPORT_PATH\"...") {
                writeToGZ(companyBusData.toJson(), BUS_COMPANY_DATA_EXPORT_PATH)
            }
            return companyBusData
        }

        fun generateDatabase(busRoutes: List<BusRoute>, busStops: List<BusStop>, minibusData: MinibusData): Database {
            lateinit var stops: List<BusStop>
            execute("Rounding coordinates...") {
                stops = busStops.map {
                    val lat = it.coordinate[0].roundCoordinate()
                    val long = it.coordinate[1].roundCoordinate()
                    it.copy(coordinate = mutableListOf(lat, long))
                }
            }

            return Database(
                version = generateVersionNumber(),
                busRoutes = busRoutes,
                busStops = stops,
                minibusRoutes = minibusData.minibusRoutes,
                minibusStops = minibusData.minibusStops
            )
        }
    }
}