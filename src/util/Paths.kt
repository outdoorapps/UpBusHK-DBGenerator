package util

import java.io.File

class Paths {
    companion object {
        const val BUS_ROUTES_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/7faa97a82780505c9673c4ba128fbfed/geojson"
        const val BUS_STOPS_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/6a20951f9a1f5c1d981e80d8a45d141c/geojson"
        const val BUS_ROUTE_STOP_URL = "https://static.data.gov.hk/td/routes-fares-geojson/JSON_BUS.json"
        const val MINIBUS_STOP_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/e9a829325a615ad382e83268228a5d26/geojson" //todo use this instead of doing all the requests
        const val BUS_FARE_URL = "https://static.data.gov.hk/td/routes-fares-xml/FARE_BUS.xml"
        // const val BUS_ROUTE_INFO_URL = "https://static.data.gov.hk/td/routes-fares-xml/ROUTE_BUS.xml"

        val resourcesDir = "resources${File.separator}"
        val tempDir = "${resourcesDir}temp${File.separator}"
        val debugDir = "${resourcesDir}debug${File.separator}"
        
        val BUS_ROUTES_GEOJSON_PATH = "${resourcesDir}BusRoute_GEOJSON.zip"
        val BUS_STOPS_GEOJSON_PATH = "${resourcesDir}CoordinateofBusStopLocation_GEOJSON.zip"
        val BUS_ROUTE_STOP_GEOJSON_PATH = "${resourcesDir}JSON_BUS.json"
        val BUS_FARE_PATH = "${resourcesDir}FARE_BUS.xml"
        val MINIBUS_STOPS_GEOJSON_PATH = "${resourcesDir}CoordinateofGMBTerminusLocation_GEOJSON.zip"

        val BUS_COMPANY_DATA_EXPORT_PATH = "${resourcesDir}bus_company_data.json.gz"
        val GOV_BUS_DATA_EXPORT_PATH = "${resourcesDir}gov_bus_data.json.gz"
        val TRACK_INFO_EXPORT_PATH = "${resourcesDir}track_info.json.gz"
        val MINIBUS_EXPORT_PATH = "${resourcesDir}minibus.json.gz"

        val DB_ROUTES_STOPS_EXPORT_PATH = "${resourcesDir}DB_routes-and-stops.json"
        val DB_PATHS_EXPORT_PATH = "${resourcesDir}DB_tracks.json"
        val DB_VERSION_EXPORT_PATH = "${resourcesDir}UpBusHK-DB-version.txt"
        const val ARCHIVE_NAME = "UpBusHK-DB"
    }
}