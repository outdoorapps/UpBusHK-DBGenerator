package utils

class APIs {
    companion object {
        const val KMB_ALL_ROUTES = "https://data.etabus.gov.hk/v1/transport/kmb/route"
        const val CTB_ALL_ROUTES = "https://rt.data.gov.hk/v2/transport/citybus/route/ctb"
        const val NLB_ALL_ROUTES = "https://rt.data.gov.hk/v2/transport/nlb/route.php?action=list"

        const val KMB_ALL_STOPS = "https://data.etabus.gov.hk/v1/transport/kmb/stop"
        const val CTB_ALL_STOP = "https://rt.data.gov.hk/v2/transport/citybus/stop"

        const val KMB_ROUTE_STOP = "https://data.etabus.gov.hk/v1/transport/kmb/route-stop"
        const val CTB_ROUTE_STOP = "https://rt.data.gov.hk/v2/transport/citybus/route-stop/ctb"
        const val NLB_ROUTE_STOP = "https://rt.data.gov.hk/v2/transport/nlb/stop.php?action=list&routeId="
    }
}
