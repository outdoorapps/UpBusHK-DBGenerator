import com.beust.klaxon.Klaxon
import data.*
import helper.BusRouteHelper
import helper.BusStopHelper
import util.Bound
import util.Company
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Utils.Companion.distanceInMeters
import util.Utils.Companion.execute
import util.Utils.Companion.getCompanies
import util.Utils.Companion.toGovStopId
import java.io.File
import java.util.zip.GZIPInputStream

// Matches bus company data and government bus data
class RouteMerger(
    private val companyBusData: CompanyBusData, private val govBusData: GovBusData
) {
    companion object {
        const val ROUTE_INFO_ERROR_DISTANCE_METERS = 220.0 // Capped for 38X's 220 m
        const val JOINT_ROUTE_ERROR_DISTANCE_METERS = 160.0
        const val CIRCULAR_ROUTE_ERROR_DISTANCE_METERS = 250.0 // Capped for CTB 25's 246 m
        const val STOP_MATCH_ERROR_DISTANCE_METERS = 50.0

        // Specific stops that require manual matching
        private val stopIdPatchMap: Map<String, Int> = mapOf("152" to 12728)
    }

    // Number to company code
    private val jointRouteCompanyCodeMap = mutableMapOf<String, String>()
    val busRoutes: List<BusRoute>
    val busStops: List<BusStop>

    init {
        lateinit var routes: MutableList<BusRoute>
        val stops = mutableListOf<BusStop>()
        stops.addAll(companyBusData.busStops.toMutableList())
        execute("Merging bus routes...", printOnNextLine = true) {
            govBusData.govBusRoutes.forEach {
                if (it.companyCode.contains("+")) {
                    jointRouteCompanyCodeMap[it.routeNameE] = it.companyCode
                }
            }

            val jointRouteMap = getJointRouteMap()
            val companyGovBusRouteMap = getCompanyGovBusRouteMap()
            val matchCount = companyGovBusRouteMap.values.count { it != null }
            val noMatchCount = companyGovBusRouteMap.keys.size - matchCount
            println("Total company routes: ${companyGovBusRouteMap.keys.size} (Government data matched: $matchCount, unmatched: $noMatchCount)")

            routes = companyGovBusRouteMap.map { (companyBusRoute, govBusRoute) ->
                val companies = if (govBusRoute != null) {
                    getCompanies(govBusRoute.companyCode)
                } else {
                    setOf(companyBusRoute.company)
                }
                val secondaryRoute = jointRouteMap[companyBusRoute]
                val secondaryStops = if (secondaryRoute == null) {
                    emptyList()
                } else {
                    getSecondaryStops(companyBusRoute, secondaryRoute)
                }
                val govStopFarePairs = govBusRoute?.stopFarePairs
                val stopFarePairs: MutableList<Pair<String, Double?>> =
                    companyBusRoute.stops.map { it to null }.toMutableList()

                if (govStopFarePairs != null) {
                    if (companyBusRoute.stops.size == govStopFarePairs.size) {
                        val fareList = govStopFarePairs.map { it.second }
                        for (i in fareList.indices) {
                            stopFarePairs[i] = stopFarePairs[i].first to fareList[i]
                        }
                    } else {
                        // If all fares are the same, fill the values (likely case for circular routes, short and does
                        // not include the final stop)
                        val fullFare = govStopFarePairs.map { it.second }.first()
                        if (fullFare != null && govStopFarePairs.map { it.second }.all { it == fullFare }) {
                            for (i in stopFarePairs.indices) {
                                stopFarePairs[i] = stopFarePairs[i].first to fullFare
                            }
                        } else {
                            // Stop location based fare matching
                            val farePairs = govStopFarePairs.toMutableList()

                            for (i in stopFarePairs.indices) {
                                val stop = companyBusData.busStops.find { it.stopId == companyBusRoute.stops[i] }
                                if (stop != null) {
                                    val closestStopId = getClosestGovStopIdWithinRange(
                                        stop, farePairs.map { it.first }.toList(), STOP_MATCH_ERROR_DISTANCE_METERS
                                    )
                                    if (closestStopId != null) {
                                        stopFarePairs[i] =
                                            stopFarePairs[i].first to farePairs.find { it.first == closestStopId }!!.second
                                        farePairs.remove(farePairs.find { it.first == closestStopId }!!)
                                    }
                                }
                            }
                        }
                    }
                }

                BusRoute(
                    companies = companies,
                    number = companyBusRoute.number,
                    bound = companyBusRoute.bound,
                    secondaryBound = secondaryRoute?.bound,
                    originEn = companyBusRoute.originEn,
                    originChiT = companyBusRoute.originChiT,
                    originChiS = companyBusRoute.originChiS,
                    destEn = companyBusRoute.destEn,
                    destChiT = companyBusRoute.destChiT,
                    destChiS = companyBusRoute.destChiS,
                    kmbServiceType = companyBusRoute.kmbServiceType,
                    nlbRouteId = companyBusRoute.nlbRouteId,
                    trackId = null,
                    fullFare = govBusRoute?.fullFare ?: stopFarePairs.map { it.second }.first(),
                    stopFarePairs = stopFarePairs,
                    stops = stopFarePairs.map { it.first },
                    secondaryStops = secondaryStops,
                    fares = stopFarePairs.map { it.second },
                    govRouteId = govBusRoute?.routeId,
                    govRouteSeq = govBusRoute?.routeSeq
                )
            }.toMutableList()
            // todo how to determine bounds on the client side from ETA data
            execute("Generating MTRB routes from gov data...") {
                val mtrbGovRoutes = BusRouteHelper.getMtrbRoutes(govBusData)
                val mtrbRoutes = mtrbGovRoutes.map {
                    BusRoute(
                        companies = setOf(Company.MTRB),
                        number = it.routeNameE,
                        bound = if (it.routeSeq == 1) Bound.O else Bound.I,
                        secondaryBound = null,
                        originEn = it.originEn,
                        originChiT = it.originChiT,
                        originChiS = it.originChiS,
                        destEn = it.destEn,
                        destChiT = it.destChiT,
                        destChiS = it.destChiS,
                        kmbServiceType = null,
                        nlbRouteId = null,
                        trackId = null,
                        fullFare = it.fullFare,
                        stopFarePairs = it.stopFarePairs.map { pair -> Pair(pair.first.toGovStopId(), pair.second) },
                        stops = it.stopFarePairs.map { pair -> pair.first.toGovStopId() },
                        secondaryStops = emptyList(),
                        fares = it.stopFarePairs.map { pair -> pair.second },
                        govRouteId = it.routeId,
                        govRouteSeq = it.routeSeq
                    )
                }
                stops.addAll(BusStopHelper.getMtrbStops(mtrbRoutes, govBusData))
                routes.addAll(mtrbRoutes)
            }

            val kmbRouteCount = routes.filter { it.companies.size == 1 && it.companies.contains(Company.KMB) }.size
            val lwbRouteCount = routes.filter { it.companies.size == 1 && it.companies.contains(Company.LWB) }.size
            val ctbRouteCount = routes.filter { it.companies.size == 1 && it.companies.contains(Company.CTB) }.size
            val nlbRouteCount = routes.filter { it.companies.size == 1 && it.companies.contains(Company.NLB) }.size
            val mtrbRouteCount = routes.filter { it.companies.size == 1 && it.companies.contains(Company.MTRB) }.size
            val jointRouteCount = routes.filter { it.companies.size > 1 }.size
            println("- Route count")
            println("-- KMB:$kmbRouteCount")
            println("-- LWB:$lwbRouteCount")
            println("-- CTB:$ctbRouteCount")
            println("-- NLB:$nlbRouteCount")
            println("-- MTRB:$mtrbRouteCount")
            println("-- Joint:$jointRouteCount")
        }

        val busRoutesWithGeneratedData = generateFare(routes)
        val fareCount = busRoutesWithGeneratedData.filter { isStopFarePairsPopulated(it) }.size
        val matchCount = routes.filter { isStopFarePairsPopulated(it) }.size
        val generatedCount = busRoutesWithGeneratedData.filter { isStopFarePairsPopulated(it) }.size - matchCount
        println("Total number of bus routes:${routes.size}, with fare:$fareCount (matched:$matchCount, generated:$generatedCount)")
        busRoutes = busRoutesWithGeneratedData
        busStops = stops
    }

    private fun generateFare(busRoutes: List<BusRoute>): List<BusRoute> {
        return busRoutes.map { busRoute ->
            var newBusRoute = busRoute
            // Match fare using data from different KMB service type
            if (busRoute.kmbServiceType != null && !isStopFarePairsPopulated(busRoute)) {
                val altRoutes = busRoutes.filter {
                    it.number == busRoute.number && it.bound == busRoute.bound && it.companies.containsAll(busRoute.companies) && isStopFarePairsPopulated(
                        it
                    )
                }
                if (altRoutes.isNotEmpty()) {
                    // Find the route with maximum non-null fares
                    val altRoute = altRoutes.maxByOrNull { route ->
                        route.stopFarePairs.map { it.second }.count { value -> value != null }
                    }
                    if (altRoute != null) {
                        val fares = altRoute.stopFarePairs.toMutableList()
                        val generatedList = busRoute.stopFarePairs.toMutableList()
                        for (i in generatedList.indices) {
                            val matchingPair = altRoute.stopFarePairs.find { it.first == generatedList[i].first }
                            if (matchingPair != null) {
                                generatedList[i] = generatedList[i].first to matchingPair.second
                                fares.remove(matchingPair)
                            }
                        }
                        newBusRoute = busRoute.copy(
                            stopFarePairs = generatedList,
                            stops = generatedList.map { it.first }.toList(),
                            fares = generatedList.map { it.second }.toList()
                        )
                    }
                }
            }
            val newStopFarePairs = newBusRoute.stopFarePairs.toMutableList()
            val newFares = newBusRoute.fares.toMutableList()
            for (i in newFares.indices) {
                if (i > 0 && newFares[i] == null) {
                    val previousNonNullFare = newFares.subList(0, i).findLast { it != null }
                    val nextNonNullFare = newFares.subList(i, newFares.size).find { it != null }
                    if (previousNonNullFare != null && nextNonNullFare != null && previousNonNullFare == nextNonNullFare) {
                        newFares[i] = previousNonNullFare
                        newStopFarePairs[i] = newStopFarePairs[i].first to previousNonNullFare
                    }
                }
            }
            newBusRoute.copy(fares = newFares)
        }
    }

    private fun isStopFarePairsPopulated(busRoute: BusRoute): Boolean =
        busRoute.stopFarePairs.map { it.second }.any { it != null }

    fun getCompanyGovBusRouteMap(): Map<CompanyBusRoute, GovBusRoute?> {
        val companyGovRouteMap = mutableMapOf<CompanyBusRoute, GovBusRoute?>()
        val jointedList = getJointedList().toMutableList()
        jointedList.forEach { companyBusRoute ->
            val candidates = getGovBusRouteCandidates(companyBusRoute).filter { govBusRoute ->
                isCompanyGovRouteBoundMatch(
                    companyBusRoute = companyBusRoute,
                    govBusRoute = govBusRoute,
                    errorDistance = ROUTE_INFO_ERROR_DISTANCE_METERS,
                )
            }

            companyGovRouteMap[companyBusRoute] = if (candidates.size == 1) {
                candidates.first()
            } else if (candidates.size > 1) {
                // stop based matching
                var matchCount = 0
                var match: GovBusRoute? = null
                candidates.forEach {
                    val c = countMatchingStops(companyBusRoute, it)
                    if (c > matchCount) {
                        matchCount = c
                        match = it
                    }
                }
                match
            } else {
                null
            }
        }
        return companyGovRouteMap
    }

    private fun countMatchingStops(companyBusRoute: CompanyBusRoute, govBusRoute: GovBusRoute): Int {
        var count = 0
        val govStops = govBusRoute.stopFarePairs.map { it.first }.toMutableList()
        companyBusRoute.stops.forEach { companyStopId ->
            val stop = companyBusData.busStops.find { e -> e.stopId == companyStopId }
            if (stop != null) {
                val closestStopId = getClosestGovStopIdWithinRange(stop, govStops, STOP_MATCH_ERROR_DISTANCE_METERS)
                if (closestStopId != null) {
                    govStops.remove(closestStopId)
                    count++
                }
            }
        }
        return count
    }

    private fun getClosestGovStopIdWithinRange(stop: BusStop, govStops: List<Int>, range: Double): Int? {
        var d = Double.MAX_VALUE
        var closestStopId: Int? = null
        govStops.forEach { govStopId ->
            val govStopCoordinate = govBusData.govStops.find { it.stopId == govStopId }?.coordinate
            if (govStopCoordinate != null) {
                val distance = distanceInMeters(stop.coordinate, govStopCoordinate)
                if (distance < range && distance < d) {
                    d = distance
                    closestStopId = govStopId
                }
            }
        }
        return closestStopId
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
                else -> false
            }
        }

    private fun isJointRoute(companyBusRoute: CompanyBusRoute): Boolean =
        jointRouteCompanyCodeMap.contains(companyBusRoute.number)

    private fun isCompanyGovRouteBoundMatch(
        companyBusRoute: CompanyBusRoute, govBusRoute: GovBusRoute, errorDistance: Double, printValues: Boolean = false
    ): Boolean {
        val comOrigin = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.first() }
        val comDestination = companyBusData.busStops.find { stop -> stop.stopId == companyBusRoute.stops.last() }
        val stStopId = govBusRoute.stopFarePairs.first().first
        val edStopId = govBusRoute.stopFarePairs.last().first
        val govOrigin = govBusData.govStops.find { it.stopId == stStopId }?.coordinate
        val govDestination = govBusData.govStops.find { it.stopId == edStopId }?.coordinate
        val originsDistance =
            if (comOrigin != null && govOrigin != null) distanceInMeters(comOrigin.coordinate, govOrigin)
            else Double.MAX_VALUE
        val destinationsDistance = if (comDestination != null && govDestination != null) distanceInMeters(
            comDestination.coordinate, govDestination
        ) else Double.MAX_VALUE
        // Circular route case, government data omit the last stop
        val govOriginComDestinationDistance =
            if (govBusRoute.originEn == govBusRoute.destEn && govOrigin != null && comDestination != null) {
                distanceInMeters(govOrigin, comDestination.coordinate)
            } else Double.MAX_VALUE

        if (printValues) {
            println("-${companyBusRoute.toInfoString()}")
            println("--origin distance:$originsDistance (${comOrigin?.coordinate},$govOrigin) (${comOrigin?.stopId}, ${stStopId})")
            println("--destination distance:$destinationsDistance (${comDestination?.coordinate},$govDestination), (${comDestination?.stopId}, ${edStopId})")
            if (govBusRoute.originEn == govBusRoute.destEn) {
                println("--GovOrigin-ComDestination distance:$govOriginComDestinationDistance ($govOrigin,${comDestination?.coordinate}), (${stStopId}, ${comDestination?.stopId})")
            }
        }
        return (originsDistance <= errorDistance || isStopMatch(
            comOrigin?.stopId, stStopId
        )) && (destinationsDistance <= errorDistance || isStopMatch(
            comDestination?.stopId, edStopId
        )) || govOriginComDestinationDistance < CIRCULAR_ROUTE_ERROR_DISTANCE_METERS
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
        val originsDistance = if (origin1 != null && origin2 != null) distanceInMeters(
            origin1, origin2
        ) else Double.MAX_VALUE
        val destinationsDistance = if (destination1 != null && destination2 != null) distanceInMeters(
            destination1, destination2
        ) else Double.MAX_VALUE
        return originsDistance <= errorDistance || destinationsDistance <= errorDistance
    }

    private fun getSecondaryStops(primaryRoute: CompanyBusRoute, secondaryRoute: CompanyBusRoute): List<String> {
        val secondaryStops = mutableListOf<String>()
        primaryRoute.stops.forEach { primaryStopId ->
            val primaryStop = companyBusData.busStops.find { x -> x.stopId == primaryStopId }
            // Search a sublist of remaining stops
            val startIndex = if (secondaryStops.isEmpty()) {
                0
            } else {
                val index = secondaryRoute.stops.indexOf(secondaryStops.last())
                if (index == secondaryRoute.stops.size) index else index + 1
            }

            val matchingStopId = if (primaryStop != null) {
                getClosestStopID(primaryStop, secondaryRoute.stops.subList(startIndex, secondaryRoute.stops.size))
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
                val distance = distanceInMeters(candidateStop.coordinate, busStop.coordinate)
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
        val jsonString = dbFile.inputStream().use { input ->
            GZIPInputStream(input).use { gzInput ->
                gzInput.bufferedReader().use { it.readText() }
            }
        }
        val data = Klaxon().parse<CompanyBusData>(jsonString)
        if (data != null) {
            companyBusData.companyBusRoutes.addAll(data.companyBusRoutes)
            companyBusData.busStops.addAll(data.busStops)
        }
    }

    val govBusData = GovDataParser.getGovBusData(loadExistingData = true, exportToFile = true)
    val routeMerger = RouteMerger(companyBusData, govBusData)
    val companyGovBusRouteMap = routeMerger.getCompanyGovBusRouteMap()
    val companyBusRouteWithMatchCount = companyGovBusRouteMap.filter { (_, v) -> v != null }.size
    val companyBusRouteWithoutMatch = companyGovBusRouteMap.filter { (_, v) -> v == null }.size
    println("Company route with a government route match: $companyBusRouteWithMatchCount, without a match: $companyBusRouteWithoutMatch")
}