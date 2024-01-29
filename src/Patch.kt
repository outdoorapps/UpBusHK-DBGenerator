import data.RequestableRoute

class Patch {
    companion object {
        // "376C4835851621D4" to "2560149CE75EABA0" TUEN MUN ROAD BBI (A6)
        // "B7A9E1A243516288" to "E5421509D8FC00AF" SHEK MUN ESTATE BUS TERMINUS
        // "E3B8D0FF5C269463" <- belong to an obsolete route A41,O,serviceType=5
        // "3236114A2BB68ACC" <- belong to obsolete routes A41,I,serviceType=5,6
        // "93BA278DCD263EF8" <- belong to obsolete routes A41,I,serviceType=5,6
        val stopPatchMap = mapOf(
            "376C4835851621D4" to "2560149CE75EABA0", "B7A9E1A243516288" to "E5421509D8FC00AF"
        )

        fun patchKmbRouteIfNeeded(requestableRoute: RequestableRoute): RequestableRoute {
            if (requestableRoute.number == "107" && requestableRoute.bound == Bound.O) {
                val newStops = requestableRoute.stops.toMutableList()
                newStops.remove("18E251745B9F2B5A")
                return requestableRoute.copy(stops = newStops)

            } else if (requestableRoute.number == "170" && requestableRoute.bound == Bound.I) {
                val newStops = requestableRoute.stops.toMutableList()
                newStops.remove("F0A8A596641FFC5A")
                return requestableRoute.copy(stops = newStops)

            } else {
                return requestableRoute
            }
        }

        fun patchCTBRoutes(ctbRoutes: MutableList<RequestableRoute>): MutableList<RequestableRoute> {
            val outboundRoute = ctbRoutes.find { it.number == "110" && it.bound == Bound.O }
            val inboundRoute = ctbRoutes.find { it.number == "110" && it.bound == Bound.I }
            ctbRoutes.remove(outboundRoute)
            ctbRoutes.remove(inboundRoute)

            if (outboundRoute != null && inboundRoute != null) {
                val outboundStops = outboundRoute.stops.toMutableList()
                val inboundStops = inboundRoute.stops
                val startIndex = inboundStops.indexOf(outboundStops.last())

                if (startIndex != -1 && startIndex < inboundStops.size) {
                    outboundStops.addAll(inboundStops.subList(startIndex + 1, inboundStops.size))
                    val newRoute = outboundRoute.copy(stops = outboundStops)
                    ctbRoutes.add(newRoute)
                }
            }
            return ctbRoutes
        }
    }
}