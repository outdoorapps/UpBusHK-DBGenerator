import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory.createCrsTransformationFirstSuccess
import com.programmerare.crsTransformations.coordinate.CrsCoordinate
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import data.*
import utils.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import utils.Paths.Companion.DB_PATHS_EXPORT_PATH
import utils.Paths.Companion.ROUTE_INFO_EXPORT_PATH
import utils.Paths.Companion.debugDir
import utils.RamerDouglasPeucker.Companion.simplify
import utils.Utils.Companion.execute
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.math.RoundingMode
import java.util.stream.Collectors
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.measureTime

class MappedRouteParser {

    companion object {
        private val crsTransformationAdapter = createCrsTransformationFirstSuccess()
        private val klaxon = Klaxon()

        // pathIDsToWrite: Write all paths if null
        // writeSeparatePathFiles: If true, write one JSON file for each path
        fun parseFile(
            parseRouteInfo: Boolean, parsePaths: Boolean, pathIDsToWrite: Set<Int>?, writeSeparatePathFiles: Boolean
        ) {
            if (parsePaths) {
                if (writeSeparatePathFiles) {
                    File(debugDir).mkdir()
                    parseGovData(parseRouteInfo, true, null, pathIDsToWrite, true)
                } else {
                    FileOutputStream(DB_PATHS_EXPORT_PATH).use {
                        it.write("{\"bus_tracks\":[".toByteArray())
                        parseGovData(parseRouteInfo, true, it, pathIDsToWrite, false)
                        it.write("]}".toByteArray())
                    }
                }
            } else {
                parseGovData(parseRouteInfo, false, null, null, false)
            }
        }

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
        private fun parseGovData(
            parseRouteInfo: Boolean,
            parsePaths: Boolean,
            pathFOS: FileOutputStream?,
            pathIDsToWrite: Set<Int>?,
            writeSeparatePathFiles: Boolean
        ) {
            val pathSizeMap = mutableMapOf<RouteInfo, Int>()
            val routeInfos = mutableListOf<RouteInfo>()
            val file = ZipFile(BUS_ROUTES_GEOJSON_PATH)
            val stream = file.getInputStream(file.entries().nextElement())
            var pathsWritten = 0

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
                                    val time = measureTime {
                                        val route = getMappedRoute(it, !parsePaths)
                                        routeInfos.add(route.routeInfo)

                                        if (writeSeparatePathFiles) {
                                            val out = FileOutputStream("$debugDir${route.routeInfo.objectId}.json")
                                            val simCoords = simplify(multilineToCoords(route.multiLineString))
                                            val busTrack = BusTrack(route.routeInfo.objectId, simCoords)
                                            out.use { out.write(busTrack.toJson().toByteArray()) }
                                            pathsWritten++
                                            pathSizeMap[route.routeInfo] = simCoords.size
                                        } else {
                                            if (pathFOS != null && (pathIDsToWrite == null || pathIDsToWrite.contains(
                                                    route.routeInfo.objectId
                                                ))
                                            ) {
                                                val simCoords = simplify(multilineToCoords(route.multiLineString))
                                                val busTrack = BusTrack(route.routeInfo.objectId, simCoords)
                                                pathFOS.write(busTrack.toJson().toByteArray())
                                                pathsWritten++
                                                pathSizeMap[route.routeInfo] = simCoords.size

                                                // Determine whether a "," should be added
                                                if (pathIDsToWrite == null) {
                                                    if (it.hasNext()) {
                                                        pathFOS.write(",".toByteArray())
                                                    }
                                                } else if (pathIDsToWrite.contains(route.routeInfo.objectId)) {
                                                    if (pathsWritten < pathIDsToWrite.size) {
                                                        pathFOS.write(",".toByteArray())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    t = t.plus(time)
                                    if (routeInfos.size % 100 == 0) {
                                        println("- ${routeInfos.size} routes parsed in $t")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pathFOS != null) {
                println("- $pathsWritten paths written")
                println("Routes with >100 coordinates:${pathSizeMap.values.filter { it > 100 }.size}")
                println("Routes with >500 coordinates:${pathSizeMap.values.filter { it > 500 }.size}")
                println("Routes with >1000 coordinates:${pathSizeMap.values.filter { it > 1000 }.size}")
                println("Routes with >2000 coordinates:${pathSizeMap.values.filter { it > 2000 }.size}")
                println("Routes with >3000 coordinates:${pathSizeMap.values.filter { it > 3000 }.size}")
                println("Max:${pathSizeMap.values.maxOrNull()}, Min:${pathSizeMap.values.minOrNull()}")
            }

            if (parseRouteInfo) {
                val routeInfoOutput = FileOutputStream(ROUTE_INFO_EXPORT_PATH)
                routeInfoOutput.use {
                    val writer: Writer = OutputStreamWriter(GZIPOutputStream(it), Charsets.UTF_8)
                    writer.use { w ->
                        w.write(Klaxon().toJsonString(routeInfos))
                    }
                }
            }
        }

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
        private fun getMappedRoute(reader: JsonReader, ignorePath: Boolean): MappedRoute {
            var routeInfo: RouteInfo? = null
            var multiLineString: List<CrsCoordinate> = listOf()
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
                                    "coordinates" -> multiLineString =
                                        getPath(reader, geometryType!! == "MultiLineString", ignorePath)
                                }
                            }
                        }
                    }
                }
            }
            return MappedRoute(routeInfo!!, multiLineString)
        }

        private fun getPath(reader: JsonReader, isMultiLineString: Boolean, ignorePath: Boolean): List<CrsCoordinate> {
            val wgs84coordinates = mutableListOf<CrsCoordinate>()
            reader.beginArray {
                while (reader.hasNext()) {
                    if (isMultiLineString) {
                        reader.beginArray {
                            val hk1980Coordinates = getCoordinates(reader, ignorePath)

                            // Removes duplicates (notes: The full path is organized as individual paths between stops.
                            // The first coordinate of the next line is the same as the last coordinate of the previous
                            // line)
                            if (wgs84coordinates.size > 0) {
                                hk1980Coordinates.removeFirst()
                            }
                            wgs84coordinates.addAll(getWgs84Coordinates(hk1980Coordinates))
                        }
                    } else {
                        val hk1980Coordinates = getCoordinates(reader, ignorePath)
                        wgs84coordinates.addAll(getWgs84Coordinates(hk1980Coordinates))
                    }
                }
            }
            return wgs84coordinates
        }

        private fun getCoordinates(reader: JsonReader, ignorePath: Boolean): List<CrsCoordinate> {
            if (ignorePath) {
                while (reader.hasNext()) {
                    reader.nextArray()
                }
                return emptyList()
            } else {
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
        }

        private fun getWgs84Coordinates(hk1980Coordinates: List<CrsCoordinate>): List<CrsCoordinate> {
            return hk1980Coordinates.parallelStream().map { hk1980Coordinate ->
                crsTransformationAdapter.transformToCoordinate(
                    hk1980Coordinate, EpsgNumber.WORLD__WGS_84__4326
                )
            }.collect(Collectors.toList())
        }

        private fun multilineToCoords(multiline: List<CrsCoordinate>) = multiline.map { crsCoordinate ->
            val lat = crsCoordinate.getLatitude().toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            val long = crsCoordinate.getLongitude().toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()
            listOf(lat, long)
        }
    }
}

fun main() {
    execute("Parsing routeInfo...", true) {
        MappedRouteParser.parseFile(
            parseRouteInfo = true, parsePaths = true, pathIDsToWrite = null, writeSeparatePathFiles = false
        )
    }
}