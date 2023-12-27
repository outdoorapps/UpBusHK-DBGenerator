import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory.createCrsTransformationMedian
import com.programmerare.crsTransformations.coordinate.CrsCoordinate
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import data.MappedRoute
import json_models.CRS
import json_models.CRSProperties
import json_models.GPSRoute
import json_models.RouteInfo
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime


class GPSRouteParser {
    companion object {
        const val SOURCE_PATH = "resources/BusRoute_GEOJSON.zip"
        const val TEST_PATH = "resources/incomplete.zip"

        private val crsTransformationAdapter = createCrsTransformationMedian()
        private val klaxon = Klaxon()

        fun readFile() {
            val file = ZipFile(SOURCE_PATH)
            val stream = file.getInputStream(file.entries().nextElement())
            val mappedRoutes = mutableListOf<MappedRoute>()
            stream.bufferedReader().use {
                JsonReader(StringReader(it.readText())).use { reader ->
                    reader.beginObject {
                        var type: String? = null
                        var name: String? = null
                        var crs: CRS? = null
                        var t = 0
                        while (reader.hasNext()) {
                            val readName = reader.nextName()
                            when (readName) {
                                "type" -> type = reader.nextString()
                                "name" -> name = reader.nextString()
                                "crs" -> crs = reader.beginObject {
                                    var crsType: String? = null
                                    var properties: CRSProperties? = null
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "type" -> crsType = reader.nextString()
                                            "properties" -> properties =
                                                klaxon.parse<CRSProperties>(reader.nextObject().toJsonString())
                                        }
                                    }
                                    CRS(crsType!!, properties!!)
                                }

                                "features" -> reader.beginArray {
                                    while (reader.hasNext()) {
                                        val time = measureTime {
                                            val route = getMappedRoute(reader)
                                            mappedRoutes.add(route)
                                            println(
                                                "#${mappedRoutes.size} Route added:${route.routeInfo.routeId}," +
                                                        "${route.routeInfo.companyCode}-${route.routeInfo.routeNameE}"
                                            )
                                        }
                                        t += time.toInt(DurationUnit.MILLISECONDS)

                                        if(mappedRoutes.size % 50 == 0) {
                                            println("Added ${mappedRoutes.size} routes in $t ms")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun getMappedRoute(reader: JsonReader): MappedRoute {
            var routeInfo: RouteInfo? = null
            var path: List<CrsCoordinate> = listOf()
            reader.beginObject {
                var featuresType: String? = null

                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "type" -> featuresType = reader.nextString()
                        "properties" -> routeInfo = klaxon.parse<RouteInfo>(reader.nextObject().toJsonString())

                        "geometry" -> reader.beginObject {
                            var geometryType: String? = null
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "type" -> geometryType = reader.nextString()
                                    "coordinates" -> path = getPath(reader)
                                }
                            }
                        }
                    }
                }
            }
            return MappedRoute(routeInfo!!, path)
        }

        private fun getPath(reader: JsonReader): List<CrsCoordinate> {
            val wgs84coordinates = mutableListOf<CrsCoordinate>()
            reader.beginArray {
                while (reader.hasNext()) {
                    val hk1980Coordinates = mutableListOf<CrsCoordinate>()
                    reader.beginArray {
                        while (reader.hasNext()) {
                            val array = reader.nextArray()
                            hk1980Coordinates.add(
                                eastingNorthing(
                                    array[0].toString().toDouble(),
                                    array[1].toString().toDouble(),
                                    EpsgNumber.CHINA__HONG_KONG__HONG_KONG_1980_GRID_SYSTEM__2326
                                )
                            )
                        }
                    }
                    // Removes duplicates
                    // Notes: The full path is organized as individual paths between stops. The first coordinate of the
                    // next line is the same as the last coordinate of the previous line
                    if (wgs84coordinates.size > 0) {
                        hk1980Coordinates.removeFirst()
                    }

                    wgs84coordinates.addAll(hk1980Coordinates.map { hk1980Coordinate ->
                        crsTransformationAdapter.transformToCoordinate(
                            hk1980Coordinate, EpsgNumber.WORLD__WGS_84__4326
                        )
                    })
                }
            }
            return wgs84coordinates
        }
    }
}

fun main() {
    GPSRouteParser.readFile()
    println("Done!")
}