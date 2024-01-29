import Patch.Companion.patchRoutes
import Patch.Companion.patchStops
import Paths.Companion.REQUESTABLES_EXPORT_PATH
import Utils.Companion.execute
import Utils.Companion.executeWithCount
import Utils.Companion.writeToGZ
import controllers.RouteController.Companion.getRoutes
import controllers.StopController
import controllers.StopController.Companion.getCtbStops
import controllers.StopController.Companion.getKmbStops
import controllers.StopController.Companion.getNlbStops
import data.Requestables

// todo log
// todo get fare
// todo MTRB routes

val requestables: Requestables = Requestables()

suspend fun main() {
    // I. Build requestable routes and stops
    buildRequestables()

    // II. Download routeInfo-path file todo
//    execute("Downloading GEOJSON file...") {
//        download(GEOJSON_URL)
//    }

    // III. Parse routeInfo
    execute("Parsing routeInfo...", true) {
        MappedRouteParser.parseFile(parseRouteInfo = true, parsePaths = false, pathIDsToWrite = null)
    }

    // IV. Run analyzer (match paths and merge routes)
    runAnalyzer()
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
    StopController.validateStops()

    // 3. Patch requestables
    execute("Patching requestables...") {
        patchRoutes(requestables.requestableRoutes)
        patchStops(requestables.requestableStops)
    }

    // 4. Write requestables
    execute("Writing requestables \"$REQUESTABLES_EXPORT_PATH\"...") {
        writeToGZ(requestables.toJson(), REQUESTABLES_EXPORT_PATH)
    }
//    execute("Writing to route only database (for debugging) \"${REQUESTABLE_ROUTES_EXPORT_PATH}\"...") {
//        val sharedDataRouteOnly = sharedData.copy(requestableStops = mutableListOf())
//        writeToGZ(sharedDataRouteOnly.toJson(), REQUESTABLE_ROUTES_EXPORT_PATH)
//    }
}