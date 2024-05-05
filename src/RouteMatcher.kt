import Analyzer.Companion.JOINT_ROUTE_ERROR_DISTANCE_METERS
import Analyzer.Companion.ROUTE_INFO_ERROR_DISTANCE_METERS
import RouteMatcher.Companion.loadGovernmentBusRouteData
import com.beust.klaxon.Klaxon
import data.*
import json_model.BusRouteStopCollection
import json_model.GovernmentRouteStop
import util.Company
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.GOVERNMENT_BUS_DATA_EXPORT_PATH
import util.Utils
import util.Utils.Companion.execute
import util.Utils.Companion.writeToGZ
import java.io.File
import java.util.zip.GZIPInputStream

// Matches bus company data and government bus data
class RouteMatcher(
    private val companyBusData: CompanyBusData, private val governmentBusRouteData: GovernmentBusRouteData
) {
    companion object {
        fun parseGovernmentBusRouteData(export: Boolean) {
            val governmentRouteStops = mutableListOf<GovernmentRouteStop>()
            val governmentBusStopCoordinates = mutableMapOf<Int, List<Double>>()
            val governmentBusRoutes = mutableListOf<GovernmentBusRoute>()
            execute("Parsing government bus route-stop data...") {
                val file = File("resources/JSON_BUS.json")
                var json: String
                file.inputStream().use { input ->
                    json = input.bufferedReader().use { it.readText() }.replace("\uFEFF", "")
                }
                val routeStops = BusRouteStopCollection.fromJson(json)?.governmentRouteStops
                if (!routeStops.isNullOrEmpty()) {
                    governmentRouteStops.addAll(routeStops)
                }
            }

            execute("Converting into routes and stops...") {
                governmentRouteStops.forEach { routeStop ->
                    val info = routeStop.info
                    governmentBusStopCoordinates[info.stopID] =
                        listOf(routeStop.geometry.longLatCoordinates[1], routeStop.geometry.longLatCoordinates[0])

                    if (!governmentBusRoutes.any { it.routeId == info.routeID && it.routeSeq == info.routeSeq }) {
                        val routeStopsOfRoute =
                            governmentRouteStops.filter { it.info.routeID == info.routeID && it.info.routeSeq == info.routeSeq }
                                .sortedBy { it.info.stopSeq }
                        if (routeStopsOfRoute.isNotEmpty()) {
                            val startStop = routeStopsOfRoute.first().info
                            val endStop = routeStopsOfRoute.last().info
                            governmentBusRoutes.add(
                                GovernmentBusRoute(
                                    routeId = info.routeID,
                                    routeSeq = info.routeSeq,
                                    companyCode = info.companyCode,
                                    routeNameE = info.routeNameE,
                                    stStopId = startStop.stopID,
                                    stStopNameE = startStop.stopNameE,
                                    stStopNameC = startStop.stopNameC,
                                    stStopNameS = startStop.stopNameS,
                                    edStopId = endStop.stopID,
                                    edStopNameE = endStop.stopNameE,
                                    edStopNameC = endStop.stopNameC,
                                    edStopNameS = endStop.stopNameS,
                                    serviceMode = info.serviceMode,
                                    specialType = info.specialType,
                                    journeyTime = info.journeyTime,
                                    fullFare = info.fullFare
                                )
                            )
                        }
                    }
                }
            }
            if (export) {
                execute("Writing government bus route-stop data to \"$GOVERNMENT_BUS_DATA_EXPORT_PATH\"...") {
                    writeToGZ(
                        GovernmentBusRouteData(governmentBusRoutes, governmentBusStopCoordinates).toJson(),
                        GOVERNMENT_BUS_DATA_EXPORT_PATH
                    )
                }
            }
        }

        fun loadGovernmentBusRouteData(): GovernmentBusRouteData {
            val governmentBusRouteData = GovernmentBusRouteData()
            execute("Loading saved government bus route data...") {
                val dbFile = File(GOVERNMENT_BUS_DATA_EXPORT_PATH)
                var jsonString: String
                dbFile.inputStream().use { input ->
                    GZIPInputStream(input).use { gzInput ->
                        jsonString = gzInput.bufferedReader().use { it.readText() }
                    }
                }
                val data = GovernmentBusRouteData.fromJson(jsonString)
                if (data != null) {
                    governmentBusRouteData.governmentBusRoutes.addAll(data.governmentBusRoutes)
                    governmentBusRouteData.governmentBusStopCoordinates.putAll(data.governmentBusStopCoordinates)
                }
            }
            return governmentBusRouteData
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
        governmentBusRouteData.governmentBusRoutes.forEach {
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

    fun getCompanyGovernmentBusRouteMap(): Map<CompanyBusRoute, GovernmentBusRoute?> {
        val companyGovernmentRouteMap = mutableMapOf<CompanyBusRoute, GovernmentBusRoute?>()
        val jointedList = getJointedList().toMutableList()
        jointedList.forEach { companyBusRoute ->
            companyGovernmentRouteMap[companyBusRoute] = getGovernmentBusRouteCandidates(companyBusRoute).find { info ->
                isCompanyGovernmentRouteBoundMatch(companyBusRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS)
            }
        }

        // todo 2nd round 200 m allowance (+10 match)
        companyGovernmentRouteMap.forEach { (t, u) ->  if(u != null) jointedList.remove(t)}
        jointedList.forEach { companyBusRoute ->
            companyGovernmentRouteMap[companyBusRoute] = getGovernmentBusRouteCandidates(companyBusRoute).find { info ->
                isCompanyGovernmentRouteBoundMatch(companyBusRoute, info, 200.0)
            }
        }
        return companyGovernmentRouteMap
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

    fun getGovernmentBusRouteCandidates(companyBusRoute: CompanyBusRoute): List<GovernmentBusRoute> =
        governmentBusRouteData.governmentBusRoutes.filter { info ->
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

    fun isCompanyGovernmentRouteBoundMatch(
        companyBusRoute: CompanyBusRoute,
        governmentBusRoute: GovernmentBusRoute,
        errorDistance: Double,
        printValues: Boolean = false
    ): Boolean {
        val origin1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.first() }?.coordinate
        val destination1 =
            companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.last() }?.coordinate
        val origin2 = governmentBusRouteData.governmentBusStopCoordinates[governmentBusRoute.stStopId]
        val destination2 = governmentBusRouteData.governmentBusStopCoordinates[governmentBusRoute.edStopId]
        val originsDistance = if (origin1 != null && origin2 != null) Utils.distanceInMeters(
            origin1, origin2
        ) else Double.MAX_VALUE
        val destinationsDistance = if (destination1 != null && destination2 != null) Utils.distanceInMeters(
            destination1, destination2
        ) else Double.MAX_VALUE
        if (printValues) {
            val originStop1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.first() }
            val destinationStop1 = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.last() }
            println(
                "--origin distance:$originsDistance ($origin1,$origin2) (${originStop1?.stopId}, ${governmentBusRoute.stStopId})" +
                        "\n--destination distance:$destinationsDistance ($destination1,$destination2), (${destinationStop1?.stopId}, ${governmentBusRoute.edStopId})"
            )
        }
        return originsDistance <= errorDistance && destinationsDistance <= errorDistance
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
    //RouteMatcher.parseGovernmentBusRouteData(true)
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

    val governmentBusRouteData = loadGovernmentBusRouteData()
    val routeMatcher = RouteMatcher(companyBusData, governmentBusRouteData)
    var companyGovernmentBusRouteMap = emptyMap<CompanyBusRoute, GovernmentBusRoute?>()
    execute("Matching company bus routes with government data...") {
        companyGovernmentBusRouteMap = routeMatcher.getCompanyGovernmentBusRouteMap()
    }

    // todo improve matching
    val companyBusRouteWithMatchCount = companyGovernmentBusRouteMap.filter { (_, v) -> v != null }.size
    val companyBusRouteWithoutMatch = companyGovernmentBusRouteMap.filter { (_, v) -> v == null }
    println("Company route with a match: $companyBusRouteWithMatchCount, Company route without a match: ${companyBusRouteWithoutMatch.size}")
    var count = 0
    companyBusRouteWithoutMatch.keys.forEach {
        val candidates = routeMatcher.getGovernmentBusRouteCandidates(it)
        if(candidates.isNotEmpty()) {
            count++
            println("$count:${it.number},${it.bound},${it.company},${it.originChiT},${it.destChiT} (this route has matching candidates)")
        }
        candidates.forEach { candidate ->
            println("-candidate:${candidate.routeNameE},${candidate.routeId},${candidate.routeSeq},${candidate.stStopNameC},${candidate.edStopNameC}")
            routeMatcher.isCompanyGovernmentRouteBoundMatch(
                it, candidate, ROUTE_INFO_ERROR_DISTANCE_METERS, printValues = true
            )
        }
    }
}