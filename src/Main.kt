import utils.HttpUtils.Companion.downloadIgnoreCertificate
import utils.Patch.Companion.patchRoutes
import utils.Patch.Companion.patchStops
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_URL
import utils.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import utils.Paths.Companion.BUS_STOPS_GEOJSON_URL
import utils.Paths.Companion.REQUESTABLES_EXPORT_PATH
import utils.Utils.Companion.execute
import utils.Utils.Companion.executeWithCount
import utils.Utils.Companion.writeToGZ
import utils.RouteUtils.Companion.getRoutes
import utils.StopUtils
import utils.StopUtils.Companion.getCtbStops
import utils.StopUtils.Companion.getKmbStops
import utils.StopUtils.Companion.getNlbStops
import data.Requestables
import utils.Company
import kotlin.time.measureTime

// todo log // private val logger: Logger = LoggerFactory.getLogger(OkHttpUtil::class.java.name)
// todo get fare
// todo MTRB routes

val requestables: Requestables = Requestables()

suspend fun main() {
    val t = measureTime {
        // I. Build requestable routes and stops
        buildRequestables()

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
        runAnalyzer()
    }
    println("Finished all tasks in $t")
}

private fun buildRequestables() {
    // 1. Get Routes
    executeWithCount("Getting KMB routes...") { getRoutes(Company.KMB) }
    executeWithCount("Getting CTB routes...") { getRoutes(Company.CTB) }
    executeWithCount("Getting NLB routes...") { getRoutes(Company.NLB) }

    // 2. Get Stops
    executeWithCount("Getting KMB stops...") { getKmbStops() }
    executeWithCount("Getting CTB stops...") { getCtbStops() }
    executeWithCount("Getting NLB stops...") { getNlbStops() }
    StopUtils.validateStops()

    // 3. Patch requestables
    execute("Patching requestables...") {
        patchRoutes(requestables.requestableRoutes)
        patchStops(requestables.requestableStops)
    }

    // 4. Write requestables
    execute("Writing requestables \"$REQUESTABLES_EXPORT_PATH\"...") {
        writeToGZ(requestables.toJson(), REQUESTABLES_EXPORT_PATH)
    }
}