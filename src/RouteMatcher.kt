import com.beust.klaxon.Klaxon
import data.*
import util.Company
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Utils
import util.Utils.Companion.execute
import java.io.File
import java.util.zip.GZIPInputStream

// Matches bus company data and government bus data
class RouteMatcher(
    private val companyBusData: CompanyBusData, private val govBusData: GovBusData
) {
    companion object {
        const val ROUTE_INFO_ERROR_DISTANCE_METERS = 150.0
        const val ROUTE_INFO_ERROR_DISTANCE_METERS_MAX = 220.0 // Capped for 38X's 220 m
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0

        // Specific stops that require manual matching
        private val stopIdPatchMap: Map<String, Int> = mapOf("152" to 12728)
    }

    private val jointRouteNumbers = mutableSetOf<String>()
    val busRoutes: List<BusRoute>

    init {
        lateinit var busRoutes: List<BusRoute>
        execute("Merging bus routes...", printOnNextLine = true) {
            govBusData.govBusRoutes.forEach {
                if (it.companyCode.contains("+")) {
                    jointRouteNumbers.add(it.routeNameE)
                }
            }
            val jointRouteMap = getJointRouteMap()
            busRoutes = getCompanyGovBusRouteMap().map { (companyBusRoute, govBusRoute) ->
                val companyCode = govBusRoute?.companyCode
                val companies = if (companyCode == null) {
                    setOf(companyBusRoute.company)
                } else {
                    Utils.getCompanies(companyCode)
                }
                val secondaryRoute = jointRouteMap[companyBusRoute]
                val secondaryStops = if (secondaryRoute == null) {
                    emptyList()
                } else {
                    getSecondaryStops(companyBusRoute, secondaryRoute)
                }
                val govStopFareMap = govBusRoute?.stopFareMap
                val stopFareMap: MutableMap<String, Double?> =
                    companyBusRoute.stops.associateWith { null }.toMutableMap()
                if (govStopFareMap != null && companyBusRoute.stops.size - 1 == govStopFareMap.size) {
                    val fareList = govStopFareMap.values.toList()
                    val stopList = stopFareMap.keys.toList()
                    for (i in govStopFareMap.values.indices) {
                        stopFareMap[stopList[i]] = fareList[i]
                    }
                }

                BusRoute(
                    companies = companies,
                    number = companyBusRoute.number,
                    bound = companyBusRoute.bound,
                    originEn = companyBusRoute.originEn,
                    originChiT = companyBusRoute.originChiT,
                    originChiS = companyBusRoute.originChiS,
                    destEn = companyBusRoute.destEn,
                    destChiT = companyBusRoute.destChiT,
                    destChiS = companyBusRoute.destChiS,
                    kmbServiceType = companyBusRoute.kmbServiceType,
                    nlbRouteId = companyBusRoute.nlbRouteId,
                    trackId = null,
                    fullFare = govBusRoute?.fullFare,
                    stopFareMap = stopFareMap,
                    secondaryStops = secondaryStops
                )
            }

            val kmbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.KMB) }.size
            val lwbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.LWB) }.size
            val ctbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.CTB) }.size
            val nlbRouteCount = busRoutes.filter { it.companies.size == 1 && it.companies.contains(Company.NLB) }.size
            val jointRouteCount = busRoutes.filter { it.companies.size > 1 }.size
            println(
                "Route count - KMB:${kmbRouteCount}, LWB:${lwbRouteCount}, CTB:${ctbRouteCount}, NLB:${nlbRouteCount}, Joint routes:${jointRouteCount}"
            )
        }
        this.busRoutes = busRoutes
    }

    fun getCompanyGovBusRouteMap(): Map<CompanyBusRoute, GovBusRoute?> {
        val companyGovRouteMap = mutableMapOf<CompanyBusRoute, GovBusRoute?>()
        val jointedList = getJointedList().toMutableList()
        jointedList.forEach { companyBusRoute ->
            companyGovRouteMap[companyBusRoute] = getGovBusRouteCandidates(companyBusRoute).find { info ->
                isCompanyGovRouteBoundMatch(companyBusRoute, info, ROUTE_INFO_ERROR_DISTANCE_METERS)
            }
        }

        // 2nd round matching using greater error allowance
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
        val kmbLwbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.KMB }
        val ctbBusCompanyRoutes = companyBusData.companyBusRoutes.filter { it.company == Company.CTB }
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
        govBusData.govBusRoutes.filter { info ->
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
        val origin2 = govBusData.govBusStopCoordinates[stStopId]
        val destination2 = govBusData.govBusStopCoordinates[edStopId]
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
        return stopIdPatchMap[stopId] == govStopId
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

    private fun getSecondaryStops(primaryRoute: CompanyBusRoute, secondaryRoute: CompanyBusRoute): List<String> {
        val secondaryStops = mutableListOf<String>()
        primaryRoute.stops.forEach { refStopId ->
            val refStop = companyBusData.busStops.find { x -> x.stopId == refStopId }
            // Search a sublist of remaining stops
            val startIndex = if (secondaryStops.isEmpty()) {
                0
            } else {
                val index = secondaryRoute.stops.indexOf(secondaryStops.last())
                if (index == secondaryRoute.stops.size) index else index + 1
            }

            val matchingStopId = if (refStop != null) {
                getClosestStopID(refStop, secondaryRoute.stops.subList(startIndex, secondaryRoute.stops.size))
            } else null
            secondaryStops.add(matchingStopId ?: "")
        }
        if (secondaryStops.contains("")) {
            for (i in secondaryStops.indices) {
                if (secondaryStops[i].isEmpty()) {
                    println("No match for StopID:${primaryRoute.stops[i]}, (${primaryRoute.number},${primaryRoute.bound},${primaryRoute.kmbServiceType})")
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

    val govDataParser = GovDataParser(loadExistingData = true, exportToFile = true)
    val routeMatcher = RouteMatcher(companyBusData, govDataParser.govBusData)
    val companyGovBusRouteMap = routeMatcher.getCompanyGovBusRouteMap()
    val companyBusRouteWithMatchCount = companyGovBusRouteMap.filter { (_, v) -> v != null }.size
    val companyBusRouteWithoutMatch = companyGovBusRouteMap.filter { (_, v) -> v == null }.size
    println("Company route with a government route match: $companyBusRouteWithMatchCount, without a match: $companyBusRouteWithoutMatch")
}