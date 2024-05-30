import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper.builder
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.houbb.opencc4j.util.ZhConverterUtil
import data.*
import json_model.GovRouteStop
import org.apache.commons.io.input.BOMInputStream
import util.Bound
import util.Paths.Companion.BUS_FARE_PATH
import util.Paths.Companion.BUS_ROUTE_STOP_JSON_PATH
import util.Paths.Companion.GOV_BUS_DATA_EXPORT_PATH
import util.Paths.Companion.GOV_MINIBUS_DATA_EXPORT_PATH
import util.Paths.Companion.MINIBUS_DATA_EXPORT_PATH
import util.Paths.Companion.MINIBUS_ROUTES_JSON_PATH
import util.Region
import util.TransportType
import util.Utils.Companion.execute
import util.Utils.Companion.standardizeChiStopName
import util.Utils.Companion.writeToGZ
import xml_model.BusFareCollection
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.time.Duration
import kotlin.time.measureTime

class GovDataParser {
    companion object {
        private val kotlinXmlMapper = builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).defaultUseWrapper(false).build()
            .registerKotlinModule()

        // RouteID to RouteSeq to listOf fare
        fun getGovBusData(loadExistingData: Boolean, exportToFile: Boolean): GovBusData {
            lateinit var busFareMap: Map<Int, Map<Int, Map<Int, Double>>>
            execute("Parsing government bus fare data...", printOnNextLine = true) {
                busFareMap = parseBusFareMap()
            }

            return if (loadExistingData) {
                lateinit var govBusData: GovBusData
                execute("Loading saved government bus route data...") {
                    val dbFile = File(GOV_BUS_DATA_EXPORT_PATH)
                    val jsonString = dbFile.inputStream().use { input ->
                        GZIPInputStream(input).use { gzInput ->
                            gzInput.bufferedReader().use { it.readText() }
                        }
                    }
                    govBusData = GovBusData.fromJson(jsonString) ?: GovBusData()
                }
                govBusData
            } else {
                val routeStops = parseGovRouteStopData(TransportType.BUS)
                generateGovBusData(routeStops, busFareMap, exportToFile = exportToFile)
            }
        }

        fun getGovMiniBusData(loadExistingData: Boolean, exportToFile: Boolean): MinibusData {
            return if (loadExistingData) {
                lateinit var govMinibusData: MinibusData
                execute("Loading saved government minibus route data...") {
                    val dbFile = File(GOV_MINIBUS_DATA_EXPORT_PATH)
                    val jsonString = dbFile.inputStream().use { input ->
                        GZIPInputStream(input).use { gzInput ->
                            gzInput.bufferedReader().use { it.readText() }
                        }
                    }
                    govMinibusData = MinibusData.fromJson(jsonString) ?: MinibusData(emptyList(), emptyList())
                }
                govMinibusData
            } else {
                val routeStops = parseGovRouteStopData(TransportType.MINIBUS)
                generateGovMinibusData(routeStops, exportToFile = exportToFile)
            }
        }

        private fun parseBusFareMap(): Map<Int, Map<Int, Map<Int, Double>>> {
            val file = File(BUS_FARE_PATH)
            val xml = file.inputStream().use { input ->
                input.bufferedReader().use { it.readText() }
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

        private fun parseGovRouteStopData(transportType: TransportType): List<GovRouteStop> {
            val govRouteStops = mutableListOf<GovRouteStop>()
            val path = when (transportType) {
                TransportType.BUS -> BUS_ROUTE_STOP_JSON_PATH
                TransportType.MINIBUS -> MINIBUS_ROUTES_JSON_PATH
            }
            val file = File(path)
            var t = Duration.ZERO

            execute("Parsing government ${transportType.name.lowercase()} route-stop data...", printOnNextLine = true) {
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
                println("${govRouteStops.size} ${transportType.name.lowercase()} route-stops parsed")
            }
            return govRouteStops
        }

        private fun generateGovBusData(
            govRouteStops: List<GovRouteStop>, busFareMap: Map<Int, Map<Int, Map<Int, Double>>>, exportToFile: Boolean
        ): GovBusData {
            val govBusData = GovBusData()
            execute("Organizing bus route-stop data into routes and stops...") {
                govRouteStops.forEach { routeStop ->
                    val info = routeStop.info
                    if (!govBusData.govBusStopCoordinates.containsKey(info.stopID)) {
                        govBusData.govBusStopCoordinates[info.stopID] =
                            listOf(routeStop.geometry.longLatCoordinates[1], routeStop.geometry.longLatCoordinates[0])
                    }

                    if (!govBusData.govBusRoutes.any { it.routeId == info.routeID && it.routeSeq == info.routeSeq }) {
                        val routeStopsOfRoute =
                            govRouteStops.filter { it.info.routeID == info.routeID && it.info.routeSeq == info.routeSeq }
                                .sortedBy { it.info.stopSeq }
                        if (routeStopsOfRoute.isNotEmpty()) {
                            val startStop = routeStopsOfRoute.first().info
                            val endStop = routeStopsOfRoute.last().info
                            val fareList = getFareList(busFareMap, info.routeID, info.routeSeq)
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
                                    originEn = startStop.stopNameE,
                                    originChiT = startStop.stopNameC,
                                    originChiS = startStop.stopNameS,
                                    destEn = endStop.stopNameE,
                                    destChiT = endStop.stopNameC,
                                    destChiS = endStop.stopNameS,
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

        private fun getFareList(
            busFareMap: Map<Int, Map<Int, Map<Int, Double>>>, govRouteID: Int, govRouteSeq: Int
        ): List<Double> {
            val routeFareMap = busFareMap[govRouteID] ?: return emptyList()
            val fareMap = routeFareMap[govRouteSeq]
            return fareMap?.values?.toList() ?: emptyList()
        }

        private fun generateGovMinibusData(govRouteStops: List<GovRouteStop>, exportToFile: Boolean): MinibusData {
            val minibusRoutes = mutableListOf<MiniBusRoute>()
            val minibusStops = mutableListOf<MinibusStop>()
            execute("Organizing minibus route-stop data into routes and stops...") {
                govRouteStops.forEach { routeStop ->
                    val info = routeStop.info
                    val stop = minibusStops.find { it.stopId == info.stopID }
                    if (stop == null) {
                        val coordinate = listOf(
                            routeStop.geometry.longLatCoordinates[1], routeStop.geometry.longLatCoordinates[0]
                        )
                        val stops = govRouteStops.filter { e -> e.info.stopID == routeStop.info.stopID }

                        // Choose the name set with the shortest standardize traditional Chinese name
                        val stopChosen = stops.minByOrNull { it.info.stopNameC.standardizeChiStopName().length }!!
                        val nameTc = stopChosen.info.stopNameC.standardizeChiStopName()

                        // Always generate simplified Chinese (Server fills it with traditional or is missing)
                        val nameSc = ZhConverterUtil.toSimple(nameTc)

                        minibusStops.add(
                            MinibusStop(
                                stopId = info.stopID,
                                engName = stopChosen.info.stopNameE,
                                chiTName = nameTc,
                                chiSName = nameSc,
                                coordinate = coordinate
                            )
                        )
                    }

                    if (!minibusRoutes.any { isOfMinibusRoute(routeStop, it) }) {
                        val routeStopsOfRoute = govRouteStops.filter {
                            it.info.routeID == info.routeID && it.info.routeSeq == info.routeSeq && it.info.district == info.district
                        }.sortedBy { it.info.stopSeq }
                        if (routeStopsOfRoute.isNotEmpty()) {
                            val startStop = routeStopsOfRoute.first().info
                            val endStop = routeStopsOfRoute.last().info

                            minibusRoutes.add(
                                MiniBusRoute(routeId = info.routeID,
                                    region = Region.fromValue(info.district),
                                    number = info.routeNameE,
                                    bound = if (info.routeSeq == 1) Bound.O else Bound.I,
                                    originEn = startStop.stopNameE,
                                    originChiT = startStop.stopNameC,
                                    originChiS = startStop.stopNameS,
                                    destEn = endStop.stopNameE,
                                    destChiT = endStop.stopNameC,
                                    destChiS = endStop.stopNameS,
                                    fullFare = info.fullFare,
                                    stops = routeStopsOfRoute.map { it.info.stopID })
                            )
                        }
                    }
                }
            }
            val minibusData = MinibusData(minibusRoutes, minibusStops)
            if (exportToFile) {
                execute("Writing government bus route-stop data to \"$GOV_MINIBUS_DATA_EXPORT_PATH\"...") {
                    writeToGZ(minibusData.toJson(), GOV_MINIBUS_DATA_EXPORT_PATH)
                }
            }
            return minibusData
        }

        private fun isOfMinibusRoute(govRouteStop: GovRouteStop, miniBusRoute: MiniBusRoute): Boolean {
            val routeSeq = if (miniBusRoute.bound == Bound.O) 1 else 2
            val info = govRouteStop.info
            return miniBusRoute.routeId == info.routeID && routeSeq == info.routeSeq && miniBusRoute.region.value == info.district
        }
    }
}


fun main() {
    val govMinibusData = GovDataParser.getGovMiniBusData(loadExistingData = false, exportToFile = true)

    lateinit var minibusData: MinibusData
    execute("Loading saved minibus route data...") {
        val dbFile = File(MINIBUS_DATA_EXPORT_PATH)
        val jsonString = dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                gzInput.bufferedReader().use { it.readText() }
            }
        }
        minibusData = MinibusData.fromJson(jsonString) ?: MinibusData(emptyList(), emptyList())
    }

    println("Gov data file stops size: ${govMinibusData.minibusRoutes.size}, Online data stops size: ${minibusData.minibusRoutes.size}")
    minibusData.minibusRoutes.forEach { route ->
        val matches =
            govMinibusData.minibusRoutes.filter { it.number == route.number && it.bound == route.bound && it.region == route.region }
        if (matches.isEmpty()) {
            println("govMinibusData missing ${route.number},${route.bound},${route.region}")
        } else {
            val matchingRoute = matches.first()

            if (!matchingRoute.stops.containsAll(route.stops) || !route.stops.containsAll(matchingRoute.stops)) {
                println("stops mismatch: ${route.number},${route.bound},${route.region}")
                println("Online data:${route.stops}")
                println("Gov data:${matchingRoute.stops}")
            }
        }
    }

    govMinibusData.minibusRoutes.forEach { route ->
        val matches =
            minibusData.minibusRoutes.filter { it.number == route.number && it.bound == route.bound && it.region == route.region }
        if (matches.isEmpty()) {
            println("online data missing ${route.number},${route.bound},${route.region}")
        }
    }

    println("Gov data file stops size: ${govMinibusData.minibusStops.size}, Online data stops size: ${minibusData.minibusStops.size}")
    minibusData.minibusStops.forEach { stop ->
        if (!govMinibusData.minibusStops.any { it.stopId == stop.stopId }) {
            println("govMinibusData missing ${stop.stopId}")
        }
    }
}
