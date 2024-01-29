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

// todo log
// todo get fare
// todo MTRB routes
fun main() {
    // 1. Get Routes
    executeWithCount("Getting KMB routes...") { getRoutes(Company.KMB) }
    executeWithCount("Getting CTB routes...") { getRoutes(Company.CTB) }
    executeWithCount("Getting NLB routes...") { getRoutes(Company.NLB) }

    // 2. Get Stops
    executeWithCount("Getting KMB stops...") { getKmbStops() }
    executeWithCount("Getting CTB stops...") { getCtbStops() }
    executeWithCount("Getting NLB stops...") { getNlbStops() }
    StopController.validateStops()

    // 3. Patch database
    execute("Patching database...") {
        patchRoutes(sharedData.requestableRoutes)
        patchStops(sharedData.requestableStops)
    }

    // 4. Write intermediate database
    execute("Writing database \"$REQUESTABLES_EXPORT_PATH\"...") {
        writeToGZ(sharedData.toJson(), REQUESTABLES_EXPORT_PATH)
    }

//    execute("Writing to route only database (for debugging) \"${REQUESTABLE_ROUTES_EXPORT_PATH}\"...") {
//        val sharedDataRouteOnly = sharedData.copy(requestableStops = mutableListOf())
//        writeToGZ(sharedDataRouteOnly.toJson(), REQUESTABLE_ROUTES_EXPORT_PATH)
//    }

}