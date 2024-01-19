import Paths.Companion.ROUTE_INFO_EXPORT_PATH
import Paths.Companion.MAPPED_ROUTES_SOURCE_PATH
import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory.createCrsTransformationFirstSuccess
import com.programmerare.crsTransformations.coordinate.CrsCoordinate
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import data.MappedRoute
import json_models.CRS
import json_models.CRSProperties
import json_models.RouteInfo
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.stream.Collectors
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.measureTime

class GPSRouteParser {
    companion object {
        private val crsTransformationAdapter = createCrsTransformationFirstSuccess()
        private val klaxon = Klaxon()

        fun readFile() {
            val file = ZipFile(MAPPED_ROUTES_SOURCE_PATH)
            val stream = file.getInputStream(file.entries().nextElement())
            val mappedRoutes = mutableListOf<MappedRoute>()
            JsonReader(stream.bufferedReader()).use {
                it.beginObject {
                    var type: String? = null
                    var name: String? = null
                    var crs: CRS? = null
                    var t = Duration.ZERO
                    while (it.hasNext()) {
                        val readName = it.nextName()
                        when (readName) {
                            "type" -> type = it.nextString()
                            "name" -> name = it.nextString()
                            "crs" -> crs = it.beginObject {
                                var crsType: String? = null
                                var properties: CRSProperties? = null
                                while (it.hasNext()) {
                                    when (it.nextName()) {
                                        "type" -> crsType = it.nextString()
                                        "properties" -> properties =
                                            klaxon.parse<CRSProperties>(it.nextObject().toJsonString())
                                    }
                                }
                                CRS(crsType!!, properties!!)
                            }

                            "features" -> it.beginArray {
                                while (it.hasNext()) {
                                    var route: MappedRoute
                                    val time = measureTime {
                                        route = getMappedRoute(it)
                                        mappedRoutes.add(route)
                                    }
                                    t = t.plus(time)
//                                    println(
//                                        "${mappedRoutes.size} Route added:${route.routeInfo.routeId}," +
//                                                "${route.routeInfo.companyCode}-${route.routeInfo.routeNameE}," +
//                                                "size:${route.path.size} in $time (total:$t, total transformations:$total)"
//                                    )
                                    if (mappedRoutes.size % 100 == 0) {
                                        println("${mappedRoutes.size} routes added in $t")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            testData.routeInfos.addAll(mappedRoutes.map { it.routeInfo })
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
                                    "coordinates" -> path = getPath(reader, geometryType!! == "MultiLineString")
                                }
                            }
                        }
                    }
                }
            }
            return MappedRoute(routeInfo!!, path)
        }

        private fun getPath(reader: JsonReader, isMultiLineString: Boolean): List<CrsCoordinate> {
            val wgs84coordinates = mutableListOf<CrsCoordinate>()
            reader.beginArray {
                while (reader.hasNext()) {
                    if (isMultiLineString) {
                        reader.beginArray {
                            val hk1980Coordinates = getCoordinates(reader)

                            // Removes duplicates (notes: The full path is organized as individual paths between stops.
                            // The first coordinate of the next line is the same as the last coordinate of the previous
                            // line)
                            if (wgs84coordinates.size > 0) {
                                hk1980Coordinates.removeFirst()
                            }
                            wgs84coordinates.addAll(getWgs84Coordinates(hk1980Coordinates))
                        }
                    } else {
                        val hk1980Coordinates = getCoordinates(reader)
                        wgs84coordinates.addAll(getWgs84Coordinates(hk1980Coordinates))
                    }
                }
            }
            return wgs84coordinates
        }

        private fun getCoordinates(reader: JsonReader): MutableList<CrsCoordinate> {
            val hk1980Coordinates = mutableListOf<CrsCoordinate>()
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
            return hk1980Coordinates
        }

        private fun getWgs84Coordinates(hk1980Coordinates: List<CrsCoordinate>): List<CrsCoordinate> {
            return hk1980Coordinates.parallelStream().map { hk1980Coordinate ->
                crsTransformationAdapter.transformToCoordinate(
                    hk1980Coordinate, EpsgNumber.WORLD__WGS_84__4326
                )
            }.collect(Collectors.toList())
        }
    }
}

fun main() {
    val t = measureTime {
        GPSRouteParser.readFile()
    }
    println("Finished in $t")

    val output = FileOutputStream(ROUTE_INFO_EXPORT_PATH)

    output.use {
        val writer: Writer = OutputStreamWriter(GZIPOutputStream(it), Charsets.UTF_8)
        writer.use { w ->
            w.write(testData.toJson())
        }
    }
}