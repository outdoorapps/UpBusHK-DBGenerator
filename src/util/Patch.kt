package util

import data.BusStop
import data.CompanyBusRoute

class Patch {
    companion object {
        // TODO 107P-I-1 should use CTB route as reference
        val accountedStops = emptyList<String>()

        private val stopPatchMap = emptyMap<String, String>()

        fun patchStops(busStops: MutableList<BusStop>) {
            stopPatchMap.forEach { (missingStopId, pairingStopId) ->
                // Check if the stop ID is truly missing
                if (!busStops.any { stop -> stop.stopId == missingStopId }) {
                    val pairingStop = busStops.find { busStop -> busStop.stopId == pairingStopId }
                    if (pairingStop != null) busStops.add(
                        BusStop(
                            Company.KMB,
                            missingStopId,
                            pairingStop.engName,
                            pairingStop.chiTName,
                            pairingStop.chiSName,
                            pairingStop.coordinate
                        )
                    )
                }
            }
            busStops.sortBy { it.stopId }
        }

        fun patchRoutes(routes: MutableList<CompanyBusRoute>) {
            // "60C1F7910C07C52B" for 115,I,1 KOWLOON CITY FERRY BUS TERMINUS (KC949), need no matching with a CTB stop,
            // KMB included two consecutive terminating stops. This, the last one, was redundant.
            val routes1 = routes.filter { it.company == Company.KMB && it.number == "115" && it.bound == Bound.I }
            routes1.forEach {
                val newStops = it.stops.toMutableList()
                newStops.remove("60C1F7910C07C52B")
                routes.remove(it)
                routes.add(it.copy(stops = newStops))
            }

            // "F0A8A596641FFC5A" for 170,I,1 SHA TIN STATION BUS TERMINUS (ST941), need no matching with a CTB stop,
            // KMB included two consecutive terminating stops. This, the last one, was redundant.
            val routes2 = routes.filter { it.company == Company.KMB && it.number == "170" && it.bound == Bound.I }
            routes2.forEach {
                val newStops = it.stops.toMutableList()
                newStops.remove("F0A8A596641FFC5A")
                routes.remove(it)
                routes.add(it.copy(stops = newStops))
            }

            patchCTBRoutes(routes)

            routes.sortWith(
                compareBy(
                    { it.company },
                    { it.number.toInt(Character.MAX_RADIX) },
                    { it.bound },
                    { it.serviceType },
                    { it.nlbRouteId?.toInt() })
            )
        }

        private fun patchCTBRoutes(routes: MutableList<CompanyBusRoute>) {
            val route110Out = routes.find { it.company == Company.CTB && it.number == "110" && it.bound == Bound.O }
            val route110In = routes.find { it.company == Company.CTB && it.number == "110" && it.bound == Bound.I }
            routes.remove(route110Out)
            routes.remove(route110In)

            if (route110Out != null && route110In != null) {
                val outboundStops = route110Out.stops.toMutableList()
                val inboundStops = route110In.stops
                val startIndex = inboundStops.indexOf(outboundStops.last())

                if (startIndex != -1 && startIndex < inboundStops.size) {
                    outboundStops.addAll(inboundStops.subList(startIndex + 1, inboundStops.size))
                    val newRoute = route110Out.copy(stops = outboundStops)
                    routes.add(newRoute)
                }
            }
        }
    }
}