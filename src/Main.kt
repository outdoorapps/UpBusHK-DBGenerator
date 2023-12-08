import controllers.RouteController.Companion.getRoutes
import controllers.StopsController.Companion.getCtbStops
import controllers.StopsController.Companion.getKmbStops
import controllers.StopsController.Companion.getNlbStops
import kotlin.time.measureTime


fun main() {
    // 1. Get Routes todo MTRB routes
//    execute("Getting KMB routes...") { getRoutes(Company.kmb) }
//    execute("Getting CTB routes...") { getRoutes(Company.ctb) }
    execute("Getting NLB routes...") { getRoutes(Company.nlb) }

    // 2. Get Stops  todo NLB MTRB stops
//    execute("Getting KMB stops...") { getKmbStops() }
//    execute("Getting CTB stops...") { getCtbStops() } //todo sort: CTB stops only
    execute("Getting NLB stops...") { getNlbStops() }

    // 3. Get Route-stops & fare

    // todo 4. Convert to JSON
    // todo proper log
}

fun execute(description: String, action : () -> Int) {
    print(description)
    var count: Int
    val t = measureTime {
        count = action()
    }
    println("added $count in $t")
}