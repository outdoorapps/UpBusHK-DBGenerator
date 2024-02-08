package utils

import data.CompanyRoute
import data.BusStop

class Patch {
    companion object {
        // "B7A9E1A243516288" to "E5421509D8FC00AF" SHEK MUN ESTATE BUS TERMINUS
        // "E3B8D0FF5C269463" <- belong to an obsolete route A41,O,serviceType=5
        // "3236114A2BB68ACC" <- belong to obsolete routes A41,I,serviceType=5,6
        // "93BA278DCD263EF8" <- belong to obsolete routes A41,I,serviceType=5,6
        private val stopPatchMap = mapOf("B7A9E1A243516288" to "E5421509D8FC00AF")

        fun patchStops(busStops: MutableList<BusStop>) {
            stopPatchMap.forEach { (missingStopId, pairingStopId) ->
                // Check if the stop ID is truly missing
                if (!busStops.any { stop -> stop.stopId == missingStopId }) {
                    val pairingStop = busStops.find { requestableStop -> requestableStop.stopId == pairingStopId }
                    if (pairingStop != null) busStops.add(
                        BusStop(
                            Company.KMB,
                            missingStopId,
                            pairingStop.engName,
                            pairingStop.chiTName,
                            pairingStop.chiSName,
                            pairingStop.latLngCoord
                        )
                    )
                }
            }
            busStops.sortBy { it.stopId }
        }

        fun patchRoutes(routes: MutableList<CompanyRoute>) {
            val routes1 = routes.filter { it.company == Company.KMB && it.number == "107" && it.bound == Bound.O }
            routes1.forEach {
                val newStops = it.stops.toMutableList()
                newStops.remove("18E251745B9F2B5A")
                routes.remove(it)
                routes.add(it.copy(stops = newStops))
            }

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
                    { it.kmbServiceType },
                    { it.routeId?.toInt() })
            )
        }

        private fun patchCTBRoutes(routes: MutableList<CompanyRoute>) {
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