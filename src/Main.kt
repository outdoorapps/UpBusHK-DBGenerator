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
import util.Paths.Companion.BUS_ROUTE_STOP_GEOJSON_PATH
import util.Paths.Companion.BUS_ROUTE_STOP_URL
import util.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import util.Paths.Companion.BUS_STOPS_GEOJSON_URL
import util.Paths.Companion.DB_VERSION_EXPORT_PATH
import util.Paths.Companion.MINIBUS_EXPORT_PATH
import util.Paths.Companion.MINIBUS_STOPS_GEOJSON_PATH
import util.Paths.Companion.MINIBUS_STOP_GEOJSON_URL
import util.Paths.Companion.resourcesDir
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.executeWithCount
import util.Utils.Companion.getArchivePath
import util.Utils.Companion.intermediates
import util.Utils.Companion.roundCoordinate
import util.Utils.Companion.writeToGZ
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.measureTime

// todo log // private val logger: Logger = LoggerFactory.getLogger(OkHttpUtil::class.java.name)
// todo get fare
// todo MTRB routes

const val compressToXZ = true
suspend fun main() {
    BasicConfigurator.configure()
    val t = measureTime {
        // I. Build routes and stops data
        val companyBusData = getBusCompanyData()
        val minibusData = getMinibusData()

        // II. Download Government data files
        downloadIgnoreCertificate(BUS_ROUTES_GEOJSON_URL, BUS_ROUTES_GEOJSON_PATH)
        downloadIgnoreCertificate(BUS_STOPS_GEOJSON_URL, BUS_STOPS_GEOJSON_PATH)
        downloadIgnoreCertificate(BUS_ROUTE_STOP_URL, BUS_ROUTE_STOP_GEOJSON_PATH)
        downloadIgnoreCertificate(BUS_FARE_URL, BUS_FARE_PATH)
        downloadIgnoreCertificate(MINIBUS_STOP_GEOJSON_URL, MINIBUS_STOPS_GEOJSON_PATH)

        // III. Parse government data
        val govDataParser = GovDataParser(loadExistingData = false, exportToFile = true)

        // IV. Match company routes with government record
        val routeMatcher = RouteMatcher(companyBusData, govDataParser.govBusData)

        // IV. Match company routes with government tracks record
        execute("Parsing trackInfo...", true) {
            TrackParser.parseFile(
                exportTrackInfoToFile = true, parsePaths = false, pathIDsToWrite = null, writeSeparatePathFiles = false
            )
        }

        val trackMatcher = TrackMatcher(companyBusData = companyBusData, govStops = null)
        val busRoutes = trackMatcher.matchTracks(routeMatcher.busRoutes)

        // V. Generate database
        val database =
            generateDatabase(busRoutes = busRoutes, busStops = companyBusData.busStops, minibusData = minibusData)

        // V. Write to archive
        execute("Writing routes and stops \"${Paths.DB_ROUTES_STOPS_EXPORT_PATH}\"...") {
            Utils.writeToJsonFile(database.toJson(), Paths.DB_ROUTES_STOPS_EXPORT_PATH)
        }

        execute("Writing paths \"${Paths.DB_PATHS_EXPORT_PATH}\"...", true) {
            val pathIDs = mutableSetOf<Int>()
            database.busRoutes.forEach { if (it.trackId != null) pathIDs.add(it.trackId) }
            TrackParser.parseFile(
                exportTrackInfoToFile = true,
                parsePaths = true,
                pathIDsToWrite = pathIDs,
                writeSeparatePathFiles = false
            )
        }
        Utils.writeToArchive(intermediates, compressToXZ = compressToXZ, deleteSource = true)

        execute("Writing version file \"${DB_VERSION_EXPORT_PATH}\"...") {
            val out = FileOutputStream(DB_VERSION_EXPORT_PATH)
            out.use {
                it.write(database.version.toByteArray())
            }
        }

        // VI. Upload to Firebase and marked changes
        //todo upload(File(getArchivePath()), database.version)
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

fun generateDatabase(busRoutes: List<BusRoute>, busStops: List<BusStop>, minibusData: MinibusData): Database {
    lateinit var stops: List<BusStop>
    execute("Rounding coordinate...") {
        stops = busStops.map {
            val lat = it.coordinate[0].roundCoordinate()
            val long = it.coordinate[1].roundCoordinate()
            it.copy(coordinate = mutableListOf(lat, long))
        }
    }

    val version = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    return Database(
        version = version,
        busRoutes = busRoutes,
        busStops = stops,
        minibusRoutes = minibusData.minibusRoutes,
        minibusStops = minibusData.minibusStops
    )
}