import TrackMatcher.Companion.intermediates
import Uploader.Companion.upload
import data.MinibusData
import data.CompanyBusData
import data.RoutesStopsDatabase
import helper.BusRouteHelper.Companion.getRoutes
import helper.BusStopHelper
import helper.MinibusHelper
import org.apache.log4j.BasicConfigurator
import util.Company
import util.HttpUtils.Companion.downloadIgnoreCertificate
import util.Patch.Companion.patchRoutes
import util.Patch.Companion.patchStops
import util.Paths
import util.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import util.Paths.Companion.BUS_ROUTES_GEOJSON_URL
import util.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import util.Paths.Companion.BUS_STOPS_GEOJSON_URL
import util.Paths.Companion.DB_VERSION_EXPORT_PATH
import util.Paths.Companion.MINIBUS_EXPORT_PATH
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.resourcesDir
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.executeWithCount
import util.Utils.Companion.getArchivePath
import util.Utils.Companion.writeToGZ
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
        // I. Build routes and stops data
        val requestedData = getBusCompanyData()
        val minibusData = getMinibusData()

        // II. Download trackInfo-path file
        execute("Downloading $BUS_ROUTES_GEOJSON_PATH ...") {
            downloadIgnoreCertificate(BUS_ROUTES_GEOJSON_URL, BUS_ROUTES_GEOJSON_PATH)
        }

        execute("Downloading $BUS_STOPS_GEOJSON_PATH ...") {
            downloadIgnoreCertificate(BUS_STOPS_GEOJSON_URL, BUS_STOPS_GEOJSON_PATH)
        }

//    execute("Downloading $BUS_ROUTE_STOP_GEOJSON_PATH ...") {
//        downloadIgnoreCertificate(BUS_ROUTE_STOP_URL, BUS_ROUTE_STOP_GEOJSON_PATH)
//    }

        // III. Parse routeInfo
        execute("Parsing trackInfo...", true) {
            MappedRouteParser.parseFile(
                exportTrackInfoToFile = true, parsePaths = false, pathIDsToWrite = null, writeSeparatePathFiles = true
            )
        }

        // IV. Run analyzer (match paths and merge routes)
        val busRSDatabase = matchTracks(requestedData)
        val rsDatabase = RoutesStopsDatabase(
            version = busRSDatabase.version,
            busRoutes = busRSDatabase.busRoutes,
            busStops = busRSDatabase.busStops,
            minibusRoutes = minibusData.minibusRoutes,
            minibusStops = minibusData.minibusStops
        )

        // V. Write to archive
        execute("Writing routes and stops \"${Paths.DB_ROUTES_STOPS_EXPORT_PATH}\"...") {
            Utils.writeToJsonFile(rsDatabase.toJson(), Paths.DB_ROUTES_STOPS_EXPORT_PATH)
        }

        execute("Writing paths \"${Paths.DB_PATHS_EXPORT_PATH}\"...", true) {
            val pathIDs = mutableSetOf<Int>()
            rsDatabase.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
            MappedRouteParser.parseFile(
                exportTrackInfoToFile = true, parsePaths = true, pathIDsToWrite = pathIDs, writeSeparatePathFiles = false
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
    busStopHelper.validateStops(companyBusData)

    // 3. Patch bus company data
    execute("Patching bus company data...") {
        patchRoutes(companyBusData.companyBusRoutes)
        patchStops(companyBusData.busStops)
    }

    // 4. Write bus company data
    execute("Writing bus company data \"$BUS_COMPANY_DATA_EXPORT_PATH\"...") {
        val dir = File(resourcesDir)
        if (!dir.exists()) dir.mkdir()
        writeToGZ(companyBusData.toJson(), BUS_COMPANY_DATA_EXPORT_PATH)
    }
    return companyBusData
}

fun getMinibusData(): MinibusData {
    lateinit var minibusData: MinibusData
    execute("Getting minibus data...", printOnNextLine = true) {
        minibusData = MinibusHelper().getMinibusData()
    }

    execute("Writing minibus data \"$MINIBUS_EXPORT_PATH\"...") {
        writeToGZ(minibusData.toJson(), MINIBUS_EXPORT_PATH)
    }
    return minibusData
}