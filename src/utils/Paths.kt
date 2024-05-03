package utils

import java.io.File

class Paths {
    companion object {
        const val BUS_ROUTES_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/7faa97a82780505c9673c4ba128fbfed/geojson"
        const val BUS_STOPS_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/6a20951f9a1f5c1d981e80d8a45d141c/geojson"

        const val BUS_FARE_URL = "https://static.data.gov.hk/td/routes-fares-xml/FARE_BUS.xml"
        const val BUS_ROUTE_INFO_URL = "https://static.data.gov.hk/td/routes-fares-xml/ROUTE_BUS.xml"

        const val BUS_ROUTES_GEOJSON_PATH = "resources/BusRoute_GEOJSON.zip"
        const val BUS_STOPS_GEOJSON_PATH = "resources/CoordinateofBusStopLocation_GEOJSON.zip"

        const val REMOTE_DATA_EXPORT_PATH = "resources/remote_data.json.gz"
        const val TRACK_INFO_EXPORT_PATH = "resources/track_info.json.gz"
        const val MINIBUS_EXPORT_PATH = "resources/minibus.json.gz"

        const val DB_ROUTES_STOPS_EXPORT_PATH = "resources/DB_routes-and-stops.json"
        const val DB_PATHS_EXPORT_PATH = "resources/DB_tracks.json"
        const val DB_VERSION_EXPORT_PATH = "resources/UpBusHK-DB-version.txt"
        const val ARCHIVE_NAME = "UpBusHK-DB"

        val resourcesDir = "resources${File.separator}"
        val tempDir = "${resourcesDir}temp${File.separator}"
        val debugDir = "${resourcesDir}debug${File.separator}"
    }
}