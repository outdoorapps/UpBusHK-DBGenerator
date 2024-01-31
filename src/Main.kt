import data.RequestedData
import utils.Company
import utils.HttpUtils.Companion.downloadIgnoreCertificate
import utils.Patch.Companion.patchRoutes
import utils.Patch.Companion.patchStops
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_URL
import utils.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import utils.Paths.Companion.BUS_STOPS_GEOJSON_URL
import utils.Paths.Companion.REQUESTABLES_EXPORT_PATH
import utils.RouteUtils.Companion.getRoutes
import utils.StopUtils
import utils.StopUtils.Companion.getCtbStops
import utils.StopUtils.Companion.getKmbStops
import utils.StopUtils.Companion.getNlbStops
import utils.Utils.Companion.execute
import utils.Utils.Companion.executeWithCount
import utils.Utils.Companion.writeToGZ
import kotlin.time.measureTime

// todo log // private val logger: Logger = LoggerFactory.getLogger(OkHttpUtil::class.java.name)
// todo get fare
// todo MTRB routes

val compressToXZ = true
suspend fun main() {
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
            MappedRouteParser.parseFile(parseRouteInfo = true, parsePaths = false, pathIDsToWrite = null)
        }

        // IV. Run analyzer (match paths and merge routes)
        runAnalyzer(requestedData)
    }
    println("Finished all tasks in $t")
}

private fun getRequestedData(): RequestedData {
    val requestedData = RequestedData()
    // 1. Get Routes
    executeWithCount("Getting KMB routes...") {
        val routes = getRoutes(Company.KMB)
        requestedData.companyRoutes.addAll(routes)
        routes.size
    }
    executeWithCount("Getting CTB routes...") {
        val routes = getRoutes(Company.CTB)
        requestedData.companyRoutes.addAll(routes)
        routes.size
    }
    executeWithCount("Getting NLB routes...") {
        val routes = getRoutes(Company.NLB)
        requestedData.companyRoutes.addAll(routes)
        routes.size
    }

    // 2. Get Stops
    executeWithCount("Getting KMB stops...") {
        val stops = getKmbStops()
        requestedData.stops.addAll(stops)
        stops.size
    }
    executeWithCount("Getting CTB stops...") {
        val stops = getCtbStops(requestedData.companyRoutes)
        requestedData.stops.addAll(stops)
        stops.size
    }
    executeWithCount("Getting NLB stops...") {
        val stops = getNlbStops(requestedData.companyRoutes)
        requestedData.stops.addAll(stops)
        stops.size
    }
    StopUtils.validateStops(requestedData)

    // 3. Patch requestables
    execute("Patching requestables...") {
        patchRoutes(requestedData.companyRoutes)
        patchStops(requestedData.stops)
    }

    // 4. Write requestables
    execute("Writing requestables \"$REQUESTABLES_EXPORT_PATH\"...") {
        writeToGZ(requestedData.toJson(), REQUESTABLES_EXPORT_PATH)
    }
    return requestedData
}