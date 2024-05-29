import RouteMatcher.Companion.loadGovBusRouteData
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper.builder
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import data.CompanyBusData
import data.CompanyBusRoute
import data.GovBusRoute
import util.Company
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.BUS_FARE_PATH
import util.Utils.Companion.execute
import xml_model.BusFareCollection
import java.io.File
import java.util.zip.GZIPInputStream

class FareParser {
    companion object {
        private val kotlinXmlMapper = builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).defaultUseWrapper(false).build()
            .registerKotlinModule()

        // RouteID to RouteSeq to listOf fare
        private var busFareMap: Map<Int, Map<Int, Map<Int, Double>>> = emptyMap()

        fun initialize() {
            if (busFareMap.isEmpty()) {
                execute("Parsing bus fare...") {
                    busFareMap = parseBusFareMap()
                }
            }
        }

        fun getFareList(govRouteID: Int, govRouteSeq: Int): List<Double> {
            val routeFareMap = busFareMap[govRouteID] ?: return emptyList()
            val fareMap = routeFareMap[govRouteSeq]
            return fareMap?.values?.toList() ?: emptyList()
        }

        private fun parseBusFareMap(): Map<Int, Map<Int, Map<Int, Double>>> {
            val file = File(BUS_FARE_PATH)
            var xml: String
            file.inputStream().use { input ->
                xml = input.bufferedReader().use { it.readText() }
            }
            val busFareCollection = kotlinXmlMapper.readValue<BusFareCollection>(xml)
            println("${busFareCollection.list.size} bus fare entries parsed")

            // RouteID to RouteSeq to StopSeq to fare
            val busFareMap = mutableMapOf<Int, MutableMap<Int, MutableMap<Int, Double>>>()
            busFareCollection.list.forEach { fareItem ->
                if (busFareMap[fareItem.routeId] == null) {
                    busFareMap[fareItem.routeId] = mutableMapOf()
                }
                if (busFareMap[fareItem.routeId]!![fareItem.routeSeq] == null) {
                    busFareMap[fareItem.routeId]!![fareItem.routeSeq] = mutableMapOf()
                }
                val fareMap = busFareMap[fareItem.routeId]!![fareItem.routeSeq]!!
                if (fareMap[fareItem.onSeq] == null) {
                    fareMap[fareItem.onSeq] = fareItem.fare
                }
            }
            // Sort by onSeq, i.e. stop sequence
            busFareMap.forEach { (_, routeFareMap) ->
                routeFareMap.forEach { (key, fareMap) ->
                    routeFareMap[key] = fareMap.toSortedMap(compareBy { it })
                }
            }
            return busFareMap
        }
    }
}


fun main() {
    FareParser.initialize()

    val companyBusData = CompanyBusData()
    execute("Loading saved bus company data...") {
        val dbFile = File(BUS_COMPANY_DATA_EXPORT_PATH)
        var jsonString: String
        dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                jsonString = gzInput.bufferedReader().use { it.readText() }
            }
        }
        val data = Klaxon().parse<CompanyBusData>(jsonString)
        if (data != null) {
            companyBusData.companyBusRoutes.addAll(data.companyBusRoutes)
            companyBusData.busStops.addAll(data.busStops)
        }
    }

    val govBusRouteData = loadGovBusRouteData()
    val routeMatcher = RouteMatcher(companyBusData, govBusRouteData)
    var companyGovBusRouteMap = emptyMap<CompanyBusRoute, GovBusRoute?>()
    execute("Matching company bus routes with government data...") {
        companyGovBusRouteMap = routeMatcher.getCompanyGovBusRouteMap()
    }

    val companyBusRouteWithMatchCount = companyGovBusRouteMap.filter { (_, v) -> v != null }.size
    val companyBusRouteWithoutMatch = companyGovBusRouteMap.filter { (_, v) -> v == null }
    println("Company route with a match: $companyBusRouteWithMatchCount, Company route without a match: ${companyBusRouteWithoutMatch.size}")

    // todo kmb routes can use service type 1 to patch fare
    var matchCount = 0
    var noMatchCount = 0
    var patchableCount = 0
    companyGovBusRouteMap.forEach { (companyBusRoute, govBusRoute) ->
        if (govBusRoute != null) {
            val fare = FareParser.getFareList(govBusRoute.routeId, govBusRoute.routeSeq)
            if (fare.isNotEmpty()) {
                if (companyBusRoute.stops.size - 1 == fare.size) {
                    matchCount++
                } else {
                    if (companyBusRoute.company == Company.KMB && (companyBusRoute.kmbServiceType != 1 || companyBusRoute.stops.size - 1 < fare.size)) {
                        patchableCount++
                    } else {
                        noMatchCount++
                        println(
                            "$noMatchCount:${companyBusRoute.number},${companyBusRoute.bound},${companyBusRoute.company},${companyBusRoute.kmbServiceType}," + "Gov:${govBusRoute.routeId},${govBusRoute.routeSeq}"
                        )
                        if (companyBusRoute.number == "967X") {
                            println(fare)
                        }
                        println("--${companyBusRoute.stops.size - 1},${fare.size} size mismatch")
                    }
                }
            }
            // todo stop size match
        }
    }
    //  println("Total government count:${companyGovBusRouteMap.values.filterNotNull().size}")
    println("Government route with a fare match: $matchCount, without a fare match: $noMatchCount")
    println("Patchable count:$patchableCount")
}
