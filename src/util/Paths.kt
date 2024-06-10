package util

import java.io.File

class Paths {
    companion object {
        const val BUS_ROUTES_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/7faa97a82780505c9673c4ba128fbfed/geojson"
        const val BUS_STOPS_GEOJSON_URL =
            "https://static.csdi.gov.hk/csdi-webpage/download/6a20951f9a1f5c1d981e80d8a45d141c/geojson"
        const val BUS_ROUTE_STOP_URL = "https://static.data.gov.hk/td/routes-fares-geojson/JSON_BUS.json"
        const val MINIBUS_ROUTES_GEOJSON_URL ="https://static.data.gov.hk/td/routes-fares-geojson/JSON_GMB.json"
        const val BUS_FARE_URL = "https://static.data.gov.hk/td/routes-fares-xml/FARE_BUS.xml"
        const val MTRB_SCHEDULE_URL = "https://rt.data.gov.hk/v1/transport/mtr/bus/getSchedule"

        val resourcesDir = "resources${File.separator}"
        val govDataDir = "${resourcesDir}govData${File.separator}"
        val generatedDir = "${resourcesDir}generated${File.separator}"
        val debugDir = "${resourcesDir}debug${File.separator}"

        val BUS_ROUTES_GEOJSON_PATH = "${govDataDir}BusRoute_GEOJSON.zip"
        val BUS_STOPS_GEOJSON_PATH = "${govDataDir}CoordinateofBusStopLocation_GEOJSON.zip"
        val BUS_ROUTE_STOP_JSON_PATH = "${govDataDir}JSON_BUS.json"
        val BUS_FARE_PATH = "${govDataDir}FARE_BUS.xml"
        val MINIBUS_ROUTES_JSON_PATH = "${govDataDir}JSON_GMB.json"

        val BUS_COMPANY_DATA_EXPORT_PATH = "${generatedDir}bus_company_data.json.gz"
        val GOV_BUS_DATA_EXPORT_PATH = "${generatedDir}gov_bus_data.json.gz"
        val TRACK_INFO_EXPORT_PATH = "${generatedDir}track_info.json.gz"
        val MINIBUS_DATA_EXPORT_PATH = "${generatedDir}minibus.json.gz"
        val GOV_MINIBUS_DATA_EXPORT_PATH = "${generatedDir}gov_minibus.json.gz"
        val MTRB_DATA_PATH = "${resourcesDir}generated/mtrb.txt"

        val DB_ROUTES_STOPS_EXPORT_PATH = "${generatedDir}DB_routes-and-stops.json"
        val DB_PATHS_EXPORT_PATH = "${generatedDir}DB_tracks.json"

        const val DATABASE_NAME = "UpBusHK-DB"
    }
}