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
    private val companyBusData: CompanyBusData,
    private val governmentBusRouteData: GovernmentBusRouteData
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
                            val startStopId = routeStopsOfRoute.first().info.stopID
                            val endStopId = routeStopsOfRoute.last().info.stopID
                            governmentBusRoutes.add(
                                GovernmentBusRoute(
                                    routeId = info.routeID,
                                    routeSeq = info.routeSeq,
                                    companyCode = info.companyCode,
                                    routeNameE = info.routeNameE,
                                    stStopId = startStopId,
                                    stStopNameE = info.locStartNameE,
                                    stStopNameC = info.locStartNameC,
                                    stStopNameS = info.locStartNameS,
                                    edStopId = endStopId,
                                    edStopNameE = info.locEndNameE,
                                    edStopNameS = info.locEndNameS,
                                    edStopNameC = info.locEndNameC,
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
            execute("Loading saved bus company data...") {
                val dbFile = File(GOVERNMENT_BUS_DATA_EXPORT_PATH)
                var jsonString: String
                dbFile.inputStream().use { input ->
                    GZIPInputStream(input).use { gzInput ->
                        jsonString = gzInput.bufferedReader().use { it.readText() }
                    }
                }
                val data = Klaxon().parse<GovernmentBusRouteData>(jsonString)
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
    private val companyGovernmentRouteMap = mutableMapOf<CompanyBusRoute, GovernmentBusRoute?>()

    private val jointRouteNumbers = mutableSetOf<String>()
    private val jointRoutes = mutableListOf<CompanyBusRoute>()

    fun initialize() {
        initialCompanyData()
    }

    fun run() {
        execute("Matching company bus routes with government data...") {
            matchCompanyAndGovernmentRoutes()
        }

        execute("Joining company bus routes...") {
            jointBusCompanyRoutes()
        }
    }

    private fun matchCompanyAndGovernmentRoutes() {
        companyBusData.companyBusRoutes.forEach { companyBusRoute ->
            companyGovernmentRouteMap[companyBusRoute] = getGovernmentBusRouteCandidates(companyBusRoute).find { info ->
                isBoundMatch(companyBusRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS)
            }
        }
    }

    private fun jointBusCompanyRoutes() {
        kmbLwbBusCompanyRoutes.forEach { kmbLwbRoute ->
            // Merge KMB/LWB and CTB routes
            val governmentBusRoutes: GovernmentBusRoute? = companyGovernmentRouteMap[kmbLwbRoute]
            val secondaryStops = mutableListOf<String>()
            if (isJointRoute(kmbLwbRoute)) {
                val ctbRoute = ctbBusCompanyRoutes.find { x ->
                    x.number == kmbLwbRoute.number && isRouteBoundMatch(
                        x, kmbLwbRoute, JOINT_ROUTE_ERROR_DISTANCE_METERS
                    )
                }
                if (ctbRoute == null) {
                    // todo add to kmbRoute
                    println(
                        "No CTB route matches KMB/LWB route: ${kmbLwbRoute.number},Bound:${kmbLwbRoute.bound}, service type:${kmbLwbRoute.kmbServiceType}"
                    )
                } else {
                    secondaryStops.addAll(getStopMap(kmbLwbRoute, ctbRoute))
                    jointRoutes.add(kmbLwbRoute)
                }
            } else {
                //todo ?
            }
            val company = if (lwbRouteNumbers.contains(kmbLwbRoute.number)) Company.LWB else Company.KMB
//            val companies = if (governmentBusRoutes != null) {
//                trackInfos.remove(governmentBusRoutes)
//                getCompanies(governmentBusRoutes.companyCode)
//            } else {
//                if (isJointRoute(kmbLwbRoute)) {
//                    unmappedJointRoutes.add(kmbLwbRoute)
//                    setOf(company, Company.CTB)
//                } else {
//                    if (company == Company.KMB) unmappedKmbRoutes.add(kmbLwbRoute)
//                    else unmappedLwbRoutes.add(kmbLwbRoute)
//                    setOf(company)
//                }
//            }
//            if (secondaryStops.isNotEmpty() && kmbLwbRoute.stops.size != secondaryStops.size) {
//                println(
//                    "Primary-secondary stops size not equal (${kmbLwbRoute.stops.size}&${secondaryStops.size}): ${kmbLwbRoute.number},${kmbLwbRoute.bound},${kmbLwbRoute.kmbServiceType}"
//                )
//            }
//            busRoutes.add(
//                BusRoute(
//                    companies,
//                    kmbLwbRoute.number,
//                    kmbLwbRoute.bound,
//                    kmbLwbRoute.originEn,
//                    kmbLwbRoute.originChiT,
//                    kmbLwbRoute.originChiS,
//                    kmbLwbRoute.destEn,
//                    kmbLwbRoute.destChiT,
//                    kmbLwbRoute.destChiS,
//                    kmbLwbRoute.kmbServiceType,
//                    null,
//                    governmentBusRoutes?.objectId,
//                    kmbLwbRoute.stops,
//                    secondaryStops
//                )
//            )
        }

    }

    private fun initialCompanyData() {
        val lwbRoutesNumbersSet = mutableSetOf<String>()
        var lwbRouteCount = 0
        governmentBusRouteData.governmentBusRoutes.forEach {
            if (it.companyCode.contains("+")) {
                jointRouteNumbers.add(it.routeNameE)
            }
            if (it.companyCode.contains(Company.LWB.value)) {
                lwbRoutesNumbersSet.add(it.routeNameE)
                if (!it.companyCode.contains("+")) lwbRouteCount++
            }
        }
        lwbRouteNumbers.addAll(lwbRoutesNumbersSet)
        val kmb = kmbLwbBusCompanyRoutes.size - lwbRouteCount
        println(
            "KMB:${kmb}, LWB:${lwbRouteCount}, CTB:${ctbBusCompanyRoutes.size}, NLB:${nlbBusCompanyRoutes.size}, Joint (unique route number):${jointRouteNumbers.size}"
        )
    }

    private fun getGovernmentBusRouteCandidates(companyBusRoute: CompanyBusRoute): List<GovernmentBusRoute> =
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

    private fun isBoundMatch(
        companyBusRoute: CompanyBusRoute, governmentBusRoute: GovernmentBusRoute, errorDistance: Double
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
        return originsDistance <= errorDistance && destinationsDistance <= errorDistance
    }

    private fun isRouteBoundMatch(
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

    var governmentBusRouteData = GovernmentBusRouteData()
    execute("Loading saved government bus data...") {
        governmentBusRouteData = loadGovernmentBusRouteData()
    }

    // 1. Load all necessary data
    val routeMatcher = RouteMatcher(companyBusData, governmentBusRouteData)
    routeMatcher.initialize()
    routeMatcher.run()
}