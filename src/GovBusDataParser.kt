import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper.builder
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import data.GovBusData
import data.GovBusRoute
import json_model.GovRouteStop
import org.apache.commons.io.input.BOMInputStream
import util.Paths.Companion.BUS_FARE_PATH
import util.Paths.Companion.BUS_ROUTE_STOP_GEOJSON_PATH
import util.Paths.Companion.GOV_BUS_DATA_EXPORT_PATH
import util.Utils.Companion.execute
import util.Utils.Companion.writeToGZ
import xml_model.BusFareCollection
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.time.Duration
import kotlin.time.measureTime

class GovBusDataParser(loadExistingData: Boolean, exportToFile: Boolean) {
    companion object {
        private val kotlinXmlMapper = builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).defaultUseWrapper(false).build()
            .registerKotlinModule()
    }

    // RouteID to RouteSeq to listOf fare
    private val busFareMap: Map<Int, Map<Int, Map<Int, Double>>>
    val govBusData: GovBusData

    init {
        lateinit var busFareMap: Map<Int, Map<Int, Map<Int, Double>>>
        execute("Parsing government bus fare data...", printOnNextLine = true) {
            busFareMap = parseBusFareMap()
        }
        this.busFareMap = busFareMap

        govBusData = if (loadExistingData) {
            loadGovBusRouteData()
        } else {
            parseGovBusRouteData(exportToFile = exportToFile)
        }
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

    private fun loadGovBusRouteData(): GovBusData {
        val govBusData = GovBusData()
        execute("Loading saved government bus route data...") {
            val dbFile = File(GOV_BUS_DATA_EXPORT_PATH)
            var jsonString: String
            dbFile.inputStream().use { input ->
                GZIPInputStream(input).use { gzInput ->
                    jsonString = gzInput.bufferedReader().use { it.readText() }
                }
            }
            val data = GovBusData.fromJson(jsonString)
            if (data != null) {
                govBusData.govBusRoutes.addAll(data.govBusRoutes)
                govBusData.govBusStopCoordinates.putAll(data.govBusStopCoordinates)
            }
        }
        return govBusData
    }

    private fun parseGovBusRouteData(exportToFile: Boolean): GovBusData {
        val govBusData = GovBusData()
        val govRouteStops = mutableListOf<GovRouteStop>()
        val file = File(BUS_ROUTE_STOP_GEOJSON_PATH)
        var t = Duration.ZERO

        execute("Parsing government bus route-stop data...", printOnNextLine = true) {
            file.inputStream().use { input ->
                BOMInputStream(input).use { bomInput ->  // Use BOM input to ignore \uFEFF
                    bomInput.bufferedReader().use { buffer ->
                        JsonReader(buffer).use {
                            it.beginObject {
                                while (it.hasNext()) {
                                    when (it.nextName()) {
                                        "type" -> it.nextString()
                                        "features" -> it.beginArray {
                                            while (it.hasNext()) {
                                                val time = measureTime {
                                                    // *Do not reuse the Klaxon object, it slows down with each successive call
                                                    val routeStop = Klaxon().parse<GovRouteStop>(it)
                                                    govRouteStops.add(routeStop!!)
                                                }
                                                t = t.plus(time)
                                                if (govRouteStops.size % 5000 == 0) {
                                                    println("${govRouteStops.size} route-stops parsed in $t")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            println("${govRouteStops.size} route-stops parsed")
        }

        execute("Organizing route-stop data into routes and stops...", printOnNextLine = true) {
            govRouteStops.forEach { routeStop ->
                val info = routeStop.info
                govBusData.govBusStopCoordinates[info.stopID] =
                    listOf(routeStop.geometry.longLatCoordinates[1], routeStop.geometry.longLatCoordinates[0])

                if (!govBusData.govBusRoutes.any { it.routeId == info.routeID && it.routeSeq == info.routeSeq }) {
                    val routeStopsOfRoute =
                        govRouteStops.filter { it.info.routeID == info.routeID && it.info.routeSeq == info.routeSeq }
                            .sortedBy { it.info.stopSeq }
                    if (routeStopsOfRoute.isNotEmpty()) {
                        val startStop = routeStopsOfRoute.first().info
                        val endStop = routeStopsOfRoute.last().info
                        val fareList = getFareList(info.routeID, info.routeSeq)
                        val stopFareMap: MutableMap<Int, Double?> =
                            routeStopsOfRoute.associate { stop -> stop.info.stopID to null }.toMutableMap()
                        if (fareList.isNotEmpty()) {
                            assert(fareList.size == routeStopsOfRoute.size - 1) //todo handle
                            if (fareList.size == routeStopsOfRoute.size - 1) {
                                for (i in fareList.indices) {
                                    stopFareMap[routeStopsOfRoute[i].info.stopID] = null //fareList[i]
                                }
                            } else {
                                println("Size mismatch:${fareList.size} and ${routeStopsOfRoute.size - 1} - ${info.routeID},${info.routeSeq},${info.companyCode},${info.routeNameE}")
                            }
                        } else {
                            println("No fare info - ${info.routeID},${info.routeSeq},${info.companyCode},${info.routeNameE}")
                        }
                        govBusData.govBusRoutes.add(
                            GovBusRoute(
                                routeId = info.routeID,
                                routeSeq = info.routeSeq,
                                companyCode = info.companyCode,
                                routeNameE = info.routeNameE,
                                stStopNameE = startStop.stopNameE,
                                stStopNameC = startStop.stopNameC,
                                stStopNameS = startStop.stopNameS,
                                edStopNameE = endStop.stopNameE,
                                edStopNameC = endStop.stopNameC,
                                edStopNameS = endStop.stopNameS,
                                serviceMode = info.serviceMode,
                                specialType = info.specialType,
                                journeyTime = info.journeyTime,
                                fullFare = info.fullFare,
                                stopFareMap = stopFareMap
                            )
                        )
                    }
                }
            }
        }
        if (exportToFile) {
            execute("Writing government bus route-stop data to \"$GOV_BUS_DATA_EXPORT_PATH\"...") {
                writeToGZ(govBusData.toJson(), GOV_BUS_DATA_EXPORT_PATH)
            }
        }
        return govBusData
    }

    private fun getFareList(govRouteID: Int, govRouteSeq: Int): List<Double> {
        val routeFareMap = busFareMap[govRouteID] ?: return emptyList()
        val fareMap = routeFareMap[govRouteSeq]
        return fareMap?.values?.toList() ?: emptyList()
    }
}


fun main() {
    GovBusDataParser(loadExistingData = false, exportToFile = true)
}
