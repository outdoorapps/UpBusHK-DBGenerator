import Analyzer.Companion.intermediates
import Uploader.Companion.upload
import data.RequestedBusData
import org.apache.log4j.BasicConfigurator
import utils.Company
import utils.HttpUtils.Companion.downloadIgnoreCertificate
import utils.Patch.Companion.patchRoutes
import utils.Patch.Companion.patchStops
import utils.Paths
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_URL
import utils.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import utils.Paths.Companion.BUS_STOPS_GEOJSON_URL
import utils.Paths.Companion.DB_VERSION_EXPORT_PATH
import utils.Paths.Companion.REQUESTABLES_EXPORT_PATH
import utils.Paths.Companion.resourcesDir
import utils.BusHelper.Companion.getRoutes
import utils.BusStopHelper
import utils.BusStopHelper.Companion.getCtbStops
import utils.BusStopHelper.Companion.getKmbStops
import utils.BusStopHelper.Companion.getNlbStops
import utils.Utils
import utils.Utils.Companion.execute
import utils.Utils.Companion.executeWithCount
import utils.Utils.Companion.getArchivePath
import utils.Utils.Companion.writeToGZ
import java.io.File
import java.io.FileOutputStream
import kotlin.time.measureTime

// todo log // private val logger: Logger = LoggerFactory.getLogger(OkHttpUtil::class.java.name)
// todo get fare
// todo MTRB routes

const val compressToXZ = true
suspend fun main() {
    BasicConfigurator.configure()
    val t = measureTime {
        // I. Build requestable routes and stops
        val requestedData = getRequestedData()

        // II. Download routeInfo-path file
        execute("Downloading $BUS_ROUTES_GEOJSON_PATH ...") {
            downloadIgnoreCertificate(BUS_ROUTES_GEOJSON_URL, BUS_ROUTES_GEOJSON_PATH)
        }

        execute("Downloading $BUS_STOPS_GEOJSON_PATH ...") {
            downloadIgnoreCertificate(BUS_STOPS_GEOJSON_URL, BUS_STOPS_GEOJSON_PATH)
        }

        // III. Parse routeInfo
        execute("Parsing routeInfo...", true) {
            MappedRouteParser.parseFile(
                parseRouteInfo = true, parsePaths = false, pathIDsToWrite = null, writeSeparatePathFiles = true
            )
        }

        // IV. Run analyzer (match paths and merge routes)
        val rsDatabase = runAnalyzer(requestedData)

        // V. Write to archive
        execute("Writing routes and stops \"${Paths.DB_ROUTES_STOPS_EXPORT_PATH}\"...") {
            Utils.writeToJsonFile(rsDatabase.toJson(), Paths.DB_ROUTES_STOPS_EXPORT_PATH)
        }

        execute("Writing paths \"${Paths.DB_PATHS_EXPORT_PATH}\"...", true) {
            val pathIDs = mutableSetOf<Int>()
            rsDatabase.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
            MappedRouteParser.parseFile(
                parseRouteInfo = true, parsePaths = true, pathIDsToWrite = pathIDs, writeSeparatePathFiles = false
            )
        }
        Utils.writeToArchive(intermediates, compressToXZ = compressToXZ, deleteSource = true)

        execute("Writing version file \"${DB_VERSION_EXPORT_PATH}\"...") {
            val out = FileOutputStream(DB_VERSION_EXPORT_PATH)
            out.use {
                it.write(rsDatabase.version.toByteArray())
            }
        }

        // VI. Upload to Firebase and marked changes
        upload(File(getArchivePath()), rsDatabase.version)
    }
    println("Finished all tasks in $t")
}

private fun getRequestedData(): RequestedBusData {
    val requestedBusData = RequestedBusData()
    // 1. Get Routes
    executeWithCount("Getting KMB routes...") {
        val routes = getRoutes(Company.KMB)
        requestedBusData.remoteBusRoutes.addAll(routes)
        routes.size
    }
    executeWithCount("Getting CTB routes...") {
        val routes = getRoutes(Company.CTB)
        requestedBusData.remoteBusRoutes.addAll(routes)
        routes.size
    }
    executeWithCount("Getting NLB routes...") {
        val routes = getRoutes(Company.NLB)
        requestedBusData.remoteBusRoutes.addAll(routes)
        routes.size
    }

    // 2. Get Stops
    executeWithCount("Getting KMB stops...") {
        val stops = getKmbStops()
        requestedBusData.busStops.addAll(stops)
        stops.size
    }
    executeWithCount("Getting CTB stops...") {
        val stops = getCtbStops(requestedBusData.remoteBusRoutes)
        requestedBusData.busStops.addAll(stops)
        stops.size
    }
    executeWithCount("Getting NLB stops...") {
        val stops = getNlbStops(requestedBusData.remoteBusRoutes)
        requestedBusData.busStops.addAll(stops)
        stops.size
    }
    BusStopHelper.validateStops(requestedBusData)

    // 3. Patch requestables
    execute("Patching requestables...") {
        patchRoutes(requestedBusData.remoteBusRoutes)
        patchStops(requestedBusData.busStops)
    }

    // 4. Write requestables
    execute("Writing requestables \"$REQUESTABLES_EXPORT_PATH\"...") {
        val dir = File(resourcesDir)
        if (!dir.exists()) dir.mkdir()
        writeToGZ(requestedBusData.toJson(), REQUESTABLES_EXPORT_PATH)
    }
    return requestedBusData
}