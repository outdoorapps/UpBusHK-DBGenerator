import com.beust.klaxon.Klaxon
import data.TestData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.time.measureTime


suspend fun main() {
    val t = measureTime {
        loadData()
    }
    println("RouteInfo:${testData.routeInfos.size}, Routes:${sharedData.routes.size}, loaded in: $t")
}

suspend fun loadData() {
    coroutineScope {
        launch {
            val klaxon = Klaxon()
            val file = File(ROUTE_INFO_EXPORT_PATH)
            val stream = GZIPInputStream(file.inputStream())
            val jsonString = stream.bufferedReader().use { it.readText() }
            val routeInfos = klaxon.parse<TestData>(jsonString)!!.routeInfos
            testData.routeInfos.addAll(routeInfos)
        }
        launch {
            val klaxon = Klaxon()
            val dbFile = File(DB_EXPORT_PATH)
            val dbStream = GZIPInputStream(dbFile.inputStream())
            val jsonString = dbStream.bufferedReader().use { it.readText() }
            val routes = klaxon.parse<SharedData>(jsonString)!!.routes
            sharedData.routes.addAll(routes)
        }
    }

}