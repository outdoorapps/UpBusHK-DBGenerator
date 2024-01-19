import Paths.Companion.DB_EXPORT_PATH
import controllers.RouteController.Companion.getRoutes
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
    executeWithCount("Getting CTB routes...") { getRoutes(Company.CTB) }
    executeWithCount("Getting NLB routes...") { getRoutes(Company.NLB) }

//    // 2. Get Stops  todo MTRB stops
    executeWithCount("Getting KMB stops...") { getKmbStops() }
    executeWithCount("Getting CTB stops...") { getCtbStops() }
    executeWithCount("Getting NLB stops...") { getNlbStops() }

    // 3. Get map and fare

    // 4. Write to Json.gz
    execute("Writing to \"$DB_EXPORT_PATH\"...") {
        val output = FileOutputStream(DB_EXPORT_PATH)
        output.use {
            val writer = OutputStreamWriter(GZIPOutputStream(it), UTF_8)
            writer.use { w ->
                w.write(sharedData.toJson())
            }
        }
    }
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