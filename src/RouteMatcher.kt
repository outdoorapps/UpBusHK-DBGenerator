import RouteMatcher.Companion.loadGovBusRouteData
import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import data.*
import json_model.GovRouteStop
import org.apache.commons.io.input.BOMInputStream
import util.Company
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.BUS_ROUTE_STOP_GEOJSON_PATH
import util.Paths.Companion.GOV_BUS_DATA_EXPORT_PATH
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.writeToGZ
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.time.Duration
import kotlin.time.measureTime

// Matches bus company data and government bus data
class RouteMatcher(
    private val companyBusData: CompanyBusData, private val govBusRouteData: GovBusRouteData
) {
    companion object {
        const val ROUTE_INFO_ERROR_DISTANCE_METERS = 150.0
        const val ROUTE_INFO_ERROR_DISTANCE_METERS_MAX = 220.0 // Capped for 38X's 220 m
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0

        private val stopIdMap: Map<String, Int> = mapOf("152" to 12728) // Specific stops that require manual matching

        fun parseGovBusRouteData(export: Boolean) {
            var t = Duration.ZERO
            val govRouteStops = mutableListOf<GovRouteStop>()
            val govBusStopCoordinates = mutableMapOf<Int, List<Double>>()
            val govBusRoutes = mutableListOf<GovBusRoute>()
            val file = File(BUS_ROUTE_STOP_GEOJSON_PATH)

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

            execute("Converting into routes and stops...", printOnNextLine = true) {
                govRouteStops.forEach { routeStop ->
                    val info = routeStop.info
                    govBusStopCoordinates[info.stopID] =
                        listOf(routeStop.geometry.longLatCoordinates[1], routeStop.geometry.longLatCoordinates[0])

                    if (!govBusRoutes.any { it.routeId == info.routeID && it.routeSeq == info.routeSeq }) {
                        val routeStopsOfRoute =
                            govRouteStops.filter { it.info.routeID == info.routeID && it.info.routeSeq == info.routeSeq }
                                .sortedBy { it.info.stopSeq }
                        if (routeStopsOfRoute.isNotEmpty()) {
                            val startStop = routeStopsOfRoute.first().info
                            val endStop = routeStopsOfRoute.last().info
                            val fareList = FareParser.getFareList(info.routeID, info.routeSeq)
                            val stopFareMap: MutableMap<Int, Double?> =
                                routeStopsOfRoute.associate { stop -> stop.info.stopID to null }.toMutableMap()
                            if (fareList.isNotEmpty()) {
                                assert(fareList.size == routeStopsOfRoute.size - 1) //todo handle
                                if (fareList.size == routeStopsOfRoute.size - 1) {
                                    for (i in fareList.indices) {
                                        stopFareMap[routeStopsOfRoute[i].info.stopID] = fareList[i]
                                    }
                                } else {
                                    println("Size mismatch:${fareList.size} and ${routeStopsOfRoute.size - 1} - ${info.routeID},${info.routeSeq},${info.companyCode},${info.routeNameE}")
                                }
                            } else {
                                println("No fare info - ${info.routeID},${info.routeSeq},${info.companyCode},${info.routeNameE}")
                            }
                            govBusRoutes.add(
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
            if (export) {
                execute("Writing government bus route-stop data to \"$GOV_BUS_DATA_EXPORT_PATH\"...") {
                    writeToGZ(
                        GovBusRouteData(govBusRoutes, govBusStopCoordinates).toJson(), GOV_BUS_DATA_EXPORT_PATH
                    )
                }
            }
        }

        fun loadGovBusRouteData(): GovBusRouteData {
            val govBusRouteData = GovBusRouteData()
            execute("Loading saved government bus route data...") {
                val dbFile = File(GOV_BUS_DATA_EXPORT_PATH)
                var jsonString: String
                dbFile.inputStream().use { input ->
                    GZIPInputStream(input).use { gzInput ->
                        jsonString = gzInput.bufferedReader().use { it.readText() }
                    }
                }
                val data = GovBusRouteData.fromJson(jsonString)
                if (data != null) {
                    govBusRouteData.govBusRoutes.addAll(data.govBusRoutes)
                    govBusRouteData.govBusStopCoordinates.putAll(data.govBusStopCoordinates)
                }
            }
            return govBusRouteData
        }
    }

    // Company Data
    private val kmbLwbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.KMB }
    private val ctbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.CTB }
    private val nlbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.NLB }

    // Generated data
    private val lwbRouteNumbers = mutableSetOf<String>()
    private val jointRouteNumbers = mutableSetOf<String>()

    init {
        var lwbRouteCount = 0
        govBusRouteData.govBusRoutes.forEach {
            if (it.companyCode.contains("+")) {
                jointRouteNumbers.add(it.routeNameE)
            }
            if (it.companyCode.contains(Company.LWB.value)) {
                lwbRouteNumbers.add(it.routeNameE)
                if (!it.companyCode.contains("+")) lwbRouteCount++ // Joint route doesn't count as LWB route
            }
        }
        val kmbRouteCount = kmbLwbBusCompanyRoutes.size - lwbRouteCount
        println(
            "KMB:${kmbRouteCount}, LWB:${lwbRouteCount}, CTB:${ctbBusCompanyRoutes.size}, NLB:${nlbBusCompanyRoutes.size}, Joint route (unique route number):${jointRouteNumbers.size}"
        )
    }

    fun getCompanyGovBusRouteMap(): Map<CompanyBusRoute, GovBusRoute?> {
        val companyGovRouteMap = mutableMapOf<CompanyBusRoute, GovBusRoute?>()
        val jointedList = getJointedList().toMutableList()
        jointedList.forEach { companyBusRoute ->
            companyGovRouteMap[companyBusRoute] = getGovBusRouteCandidates(companyBusRoute).find { info ->
                isCompanyGovRouteBoundMatch(companyBusRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS)
            }
        }

        // 2nd round matching using greater allowance
        companyGovRouteMap.forEach { (t, u) -> if (u != null) jointedList.remove(t) }
        jointedList.forEach { companyBusRoute ->
            companyGovRouteMap[companyBusRoute] = getGovBusRouteCandidates(companyBusRoute).find { info ->
                isCompanyGovRouteBoundMatch(companyBusRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS_MAX)
            }
        }
        return companyGovRouteMap
    }

    // Return a list of all routes minus the joint route duplicates (for a KMB/LWB + CTB route, only the KMB/LWB route remains)
    private fun getJointedList(): List<CompanyBusRoute> {
        val list = mutableListOf<CompanyBusRoute>()
        val jointRouteMap = getJointRouteMap()
        list.addAll(companyBusData.companyBusRoutes)
        jointRouteMap.values.forEach { list.remove(it) }
        return list
    }

    // Returns a KMB/LWB to CTB route map
    private fun getJointRouteMap(): Map<CompanyBusRoute, CompanyBusRoute> {
        val jointRouteMap = mutableMapOf<CompanyBusRoute, CompanyBusRoute>()
        kmbLwbBusCompanyRoutes.forEach { kmbLwbRoute ->
            if (isJointRoute(kmbLwbRoute)) {
                val ctbRoute = ctbBusCompanyRoutes.find { x ->
                    x.number == kmbLwbRoute.number && isCompanyRouteBoundMatch(
                        x, kmbLwbRoute, JOINT_ROUTE_ERROR_DISTANCE_METERS
                    )
                }
                if (ctbRoute == null) {
                    println(
                        "No CTB route matches KMB/LWB route: ${kmbLwbRoute.number},Bound:${kmbLwbRoute.bound}, service type:${kmbLwbRoute.kmbServiceType}"
                    )
                } else {
                    jointRouteMap[kmbLwbRoute] = ctbRoute
                }
            }
        }
        return jointRouteMap
    }

    private fun getGovBusRouteCandidates(companyBusRoute: CompanyBusRoute): List<GovBusRoute> =
        govBusRouteData.govBusRoutes.filter { info ->
            when (companyBusRoute.company) {
                Company.KMB, Company.LWB -> (info.companyCode.contains(companyBusRoute.company.value) || info.companyCode.contains(
                    Company.LWB.value
                )) && info.routeNameE == companyBusRoute.number

                Company.CTB, Company.NLB -> info.companyCode.contains(companyBusRoute.company.value) && info.routeNameE == companyBusRoute.number
                Company.MTRB -> TODO()
            }
        }

    private fun isJointRoute(companyBusRoute: CompanyBusRoute): Boolean =
        jointRouteNumbers.contains(companyBusRoute.number)

    private fun isCompanyGovRouteBoundMatch(
        companyBusRoute: CompanyBusRoute, govBusRoute: GovBusRoute, errorDistance: Double, printValues: Boolean = false
    ): Boolean {
        val origin1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.first() }
        val destination1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.last() }
        val stStopId = govBusRoute.stopFareMap.keys.first()
        val edStopId = govBusRoute.stopFareMap.keys.last()
        val origin2 = govBusRouteData.govBusStopCoordinates[stStopId]
        val destination2 = govBusRouteData.govBusStopCoordinates[edStopId]
        val originsDistance =
            if (origin1 != null && origin2 != null) Utils.distanceInMeters(origin1.coordinate, origin2)
            else Double.MAX_VALUE
        val destinationsDistance = if (destination1 != null && destination2 != null) Utils.distanceInMeters(
            destination1.coordinate, destination2
        ) else Double.MAX_VALUE
        if (printValues) {
            println("--origin distance:$originsDistance (${origin1?.coordinate},$origin2) (${origin1?.stopId}, ${stStopId})")
            println("--destination distance:$destinationsDistance (${destination1?.coordinate},$destination2), (${destination1?.stopId}, ${edStopId})")
        }
        return (originsDistance <= errorDistance || isStopMatch(
            origin1?.stopId, stStopId
        )) && (destinationsDistance <= errorDistance || isStopMatch(
            destination1?.stopId, edStopId
        ))
    }

    private fun isStopMatch(stopId: String?, govStopId: Int): Boolean {
        if (stopId == null) return false
        return stopIdMap[stopId] == govStopId
    }

    private fun isCompanyRouteBoundMatch(
        companyBusRoute1: CompanyBusRoute, companyBusRoute2: CompanyBusRoute, errorDistance: Double
    ): Boolean {
        val origin1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute1.stops.first() }?.coordinate
        val destination1 =
            companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute1.stops.last() }?.coordinate
        val origin2 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute2.stops.first() }?.coordinate
        val destination2 =
            companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute2.stops.last() }?.coordinate
        val originsDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1, origin2
        ) else Double.MAX_VALUE
        val destinationsDistance = if (destination1 != null && destination2 != null) Utils.distanceInMeters(
            destination1, destination2
        ) else Double.MAX_VALUE
        return originsDistance <= errorDistance || destinationsDistance <= errorDistance
    }

    private fun getStopMap(refRoute: CompanyBusRoute, matchingRoute: CompanyBusRoute): List<String> {
        val secondaryStops = mutableListOf<String>()
        refRoute.stops.forEach { refStopId ->
            val refStop = companyBusData.busStops.find { x -> x.stopId == refStopId }
            // Search a sublist of remaining stops
            val startIndex = if (secondaryStops.isEmpty()) {
                0
            } else {
                val index = matchingRoute.stops.indexOf(secondaryStops.last())
                if (index == matchingRoute.stops.size) index else index + 1
            }

            val matchingStopId = if (refStop != null) {
                getClosestStopID(refStop, matchingRoute.stops.subList(startIndex, matchingRoute.stops.size))
            } else null
            secondaryStops.add(matchingStopId ?: "")
        }
        if (secondaryStops.contains("")) {
            for (i in secondaryStops.indices) {
                if (secondaryStops[i].isEmpty()) {
                    println("Not match for StopID:${refRoute.stops[i]}, (${refRoute.number},${refRoute.bound},${refRoute.kmbServiceType})")
                }
            }
        }
        return secondaryStops
    }

    private fun getClosestStopID(busStop: BusStop, candidateStopIDs: List<String>): String? {
        var result: String? = null
        var minDistance = Double.MAX_VALUE
        candidateStopIDs.forEach {
            val candidateStop = companyBusData.busStops.find { x -> x.stopId == it }
            if (candidateStop != null) {
                val distance = Utils.distanceInMeters(candidateStop.coordinate, busStop.coordinate)
                if (distance < minDistance) {
                    minDistance = distance
                    result = candidateStop.stopId
                }
            }
        }
        return result
    }

    private fun getCompanies(companyCode: String): Set<Company> =
        companyCode.split("+").map { Company.fromValue(it) }.toSet()
}

fun main() {
    FareParser.initialize()
    //RouteMatcher.parseGovBusRouteData(true)

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

    //todo do Stop-based matching


    // 6 unmatched alternative routes, 59 CTB unmatched routes, 182 routes without matching candidates
//    var count = 0
//    companyBusRouteWithoutMatch.keys.forEach {
//        val candidates = routeMatcher.getGovernmentBusRouteCandidates(it)
//        if (candidates.isNotEmpty()) {
//            if (it.company != Company.CTB) {
//                count++
//                println("$count:${it.number},${it.bound},${it.company},${it.originChiT},${it.destChiT} (this route has matching candidates)")
//                candidates.forEach { candidate ->
//                    println("-candidate:${candidate.routeNameE},${candidate.routeId},${candidate.routeSeq},${candidate.stStopNameC},${candidate.edStopNameC}")
//                    routeMatcher.isCompanyGovernmentRouteBoundMatch(
//                        it, candidate, ROUTE_INFO_ERROR_DISTANCE_METERS, printValues = true
//                    )
//                }
//            }
//        }
//    }
}