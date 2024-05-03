import FareParser.Companion.getBusFareMap
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper.builder
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import data.BusFareRoot
import data.BusRouteInfoRoot
import data.RemoteBusData
import utils.Paths.Companion.REMOTE_DATA_EXPORT_PATH
import utils.Utils.Companion.execute
import java.io.File
import java.util.zip.GZIPInputStream

class FareParser {
    companion object {
        val kotlinXmlMapper = builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .defaultUseWrapper(false).build().registerKotlinModule()

        fun getBusFareMap() : MutableMap<String, MutableList<Pair<Int, Double>>> {
            val file = File("resources/FARE_BUS.xml")
            val dbStream = file.inputStream()
            val xml = dbStream.bufferedReader().use { it.readText() }
            val busFareRoot = kotlinXmlMapper.readValue<BusFareRoot>(xml)
            println("Bus fare parsed with ${busFareRoot.list.size} entries") // the first action!!!

            val busFareMap = mutableMapOf<String, MutableList<Pair<Int, Double>>>()
            busFareRoot.list.forEach { fareItem ->
                val id = "${fareItem.routeId}-${fareItem.routeSeq}"
                if (busFareMap[id] == null) {
                    busFareMap[id] = mutableListOf()
                }
                val fareList = busFareMap[id]!!
                if (fareList.none { it.first == fareItem.onSeq }) {
                    fareList.add(Pair(fareItem.onSeq, fareItem.fare))
                }
            }
            busFareMap.forEach { (_, list) -> list.sortBy { it.first } }

            println("Keys: ${busFareMap.keys.size}")
            println(busFareMap["1757-2"])
            println(busFareRoot.list.filter { it.routeId == 1757 && it.routeSeq == 1 }.size)
            return busFareMap
        }
    }
}


fun main() {
    val fareMap = getBusFareMap()

    val remoteBusData = RemoteBusData()
    execute("Loading saved requested data...") {
        val dbFile = File(REMOTE_DATA_EXPORT_PATH)
        val dbStream = GZIPInputStream(dbFile.inputStream())
        val jsonString = dbStream.bufferedReader().use { it.readText() }
        val data = Klaxon().parse<RemoteBusData>(jsonString)
        if (data != null) {
            remoteBusData.remoteBusRoutes.addAll(data.remoteBusRoutes)
            remoteBusData.busStops.addAll(data.busStops)
        }
    }

//    val file = File("resources/ROUTE_BUS.xml")
//    val dbStream = file.inputStream()
//    val xml = dbStream.bufferedReader().use { it.readText() }
//    val busRouteInfoRoot = kotlinXmlMapper.readValue<BusRouteInfoRoot>(xml)
//
//    val routeIDs = fareMap.keys.map { it.split("-")[0]}
//    println(routeIDs)
//
//    var count = 0
//    routeIDs.forEach {
//        // this match route number and company but not bound
//        if(busRouteInfoRoot.list.any { info -> info.routeID == it.toInt() }) {
//            count++
//        }
//    }
//    print("Matching $count/${routeIDs.size}")
//    //println(busRouteInfoRoot.list.size)
//    println("RouteSeq 2 size:${fareMap.keys.filter { it.contains("-2") }.size}")

}
