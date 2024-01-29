import Patch.Companion.patchRoutes
import Patch.Companion.patchStops
import Paths.Companion.DB_EXPORT_PATH
import Paths.Companion.DB_ROUTE_ONLY_EXPORT_PATH
import controllers.RouteController.Companion.getRoutes
import controllers.StopController
import controllers.StopController.Companion.getCtbStops
import controllers.StopController.Companion.getKmbStops
import controllers.StopController.Companion.getNlbStops
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream
import kotlin.text.Charsets.UTF_8
import kotlin.time.measureTime

// todo proper log
// todo not finishing at the end, okhttp?
fun main() {
    // 1. Get Routes todo MTRB routes
    executeWithCount("Getting KMB routes...") { getRoutes(Company.KMB) }
    executeWithCount("Getting CTB routes...") {
        println()
        getRoutes(Company.CTB)
    }
    executeWithCount("Getting NLB routes...") { getRoutes(Company.NLB) }

    // 2. Get Stops  todo MTRB stops
    executeWithCount("Getting KMB stops...") { getKmbStops() }
    executeWithCount("Getting CTB stops...") {
        println()
        getCtbStops()
    }
    executeWithCount("Getting NLB stops...") { getNlbStops() }
    StopController.validateStops()

    // 3. Patch database
    execute("Patching database...") {
        patchRoutes(sharedData.requestableRoutes)
        patchStops(sharedData.requestableStops)
    }

    // 4. Write intermediate database
    execute("Writing database \"$DB_EXPORT_PATH\"...") {
        writeToFile(sharedData.toJson(), DB_EXPORT_PATH)
    }

    execute("Writing to route only database (for debugging) \"${DB_ROUTE_ONLY_EXPORT_PATH}\"...") {
        val sharedDataRouteOnly = sharedData.copy(requestableStops = mutableListOf())
        writeToFile(sharedDataRouteOnly.toJson(), DB_ROUTE_ONLY_EXPORT_PATH)
    }

    // todo get fare
}

fun executeWithCount(description: String, action: () -> Int) {
    print(description)
    var count: Int
    val t = measureTime {
        count = action()
    }
    println("added $count in $t")
}

fun execute(description: String, action: () -> Unit) {
    print(description)
    val t = measureTime { action() }
    println("finished in $t")
}

fun writeToFile(data: String, path: String) {
    val output = FileOutputStream(path)
    output.use {
        val writer = OutputStreamWriter(GZIPOutputStream(it), UTF_8)
        writer.use { w ->
            w.write(data)
        }
    }
}