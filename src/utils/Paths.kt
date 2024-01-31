package utils

class Paths {
    companion object {
        const val BUS_ROUTES_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/7faa97a82780505c9673c4ba128fbfed/geojson"
        const val BUS_STOPS_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/6a20951f9a1f5c1d981e80d8a45d141c/geojson"
        const val BUS_ROUTES_GEOJSON_PATH = "resources/BusRoute_GEOJSON.zip"
        const val BUS_STOPS_GEOJSON_PATH = "resources/CoordinateofBusStopLocation_GEOJSON.zip"

        const val REQUESTABLES_EXPORT_PATH = "resources/requestables.json.gz"
        const val ROUTE_INFO_EXPORT_PATH = "resources/route_info.json.gz"

        const val DB_ROUTES_STOPS_EXPORT_PATH = "resources/DB_routes-and-stops.json"
        const val DB_PATHS_EXPORT_PATH = "resources/DB_paths.json"
        const val ARCHIVE_EXPORT_PATH = "resources/UpBusHK-DB.tar.xz"
        const val ARCHIVE_EXPORT_PATH_GZ = "resources/UpBusHK-DB.tar.gz"
    }
}