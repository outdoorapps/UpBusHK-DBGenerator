import com.beust.klaxon.JsonReader
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory.createCrsTransformationFirstSuccess
import com.programmerare.crsTransformations.coordinate.CrsCoordinate
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import data.Track
import data.GovTrack
import data.TrackInfo
import json_model.CRS
import json_model.CRSProperties
import util.Paths.Companion.BUS_ROUTES_GEOJSON_PATH
import util.Paths.Companion.DB_PATHS_EXPORT_PATH
import util.Paths.Companion.TRACK_INFO_EXPORT_PATH
import util.Paths.Companion.debugDir
import util.RamerDouglasPeucker.Companion.simplify
import util.Utils.Companion.execute
import util.Utils.Companion.roundCoordinate
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.stream.Collectors
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import kotlin.time.Duration
import kotlin.time.measureTime

// Convert tracks from government data file to standardized data
class TrackParser {
    companion object {
        private val crsTransformationAdapter = createCrsTransformationFirstSuccess()

        // pathIDsToWrite: Write all paths if null
        // writeSeparatePathFiles: If true, write one JSON file for each path
        fun parseFile(
            exportTrackInfoToFile: Boolean,
            parsePaths: Boolean,
            pathIDsToWrite: Set<Int>?,
            writeSeparatePathFiles: Boolean
        ) {
            if (parsePaths) {
                if (writeSeparatePathFiles) {
                    parseGovTrackData(exportTrackInfoToFile, true, null, pathIDsToWrite, true)
                } else {
                    FileOutputStream(DB_PATHS_EXPORT_PATH).use {
                        it.write("{\"bus_tracks\":[".toByteArray())
                        parseGovTrackData(exportTrackInfoToFile, true, it, pathIDsToWrite, false)
                        it.write("]}".toByteArray())
                    }
                }
            } else {
                parseGovTrackData(exportTrackInfoToFile, false, null, null, false)
            }
        }

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
        private fun parseGovTrackData(
            parseTrackInfo: Boolean,
            parsePaths: Boolean,
            pathFOS: FileOutputStream?,
            pathIDsToWrite: Set<Int>?,
            writeSeparatePathFiles: Boolean
        ) {
            val pathSizeMap = mutableMapOf<TrackInfo, Int>()
            val trackInfos = mutableListOf<TrackInfo>()
            val file = ZipFile(BUS_ROUTES_GEOJSON_PATH)
            var pathsWritten = 0

            file.getInputStream(file.entries().nextElement()).use { input ->
                input.bufferedReader().use { buffer ->
                    JsonReader(buffer).use {
                        it.beginObject {
                            var type: String? = null
                            var name: String? = null
                            var crs: CRS? = null
                            var t = Duration.ZERO
                            while (it.hasNext()) {
                                when (it.nextName()) {
                                    "type" -> type = it.nextString()
                                    "name" -> name = it.nextString()
                                    "crs" -> crs = it.beginObject {
                                        var crsType: String? = null
                                        var properties: CRSProperties? = null
                                        while (it.hasNext()) {
                                            when (it.nextName()) {
                                                "type" -> crsType = it.nextString()
                                                "properties" -> properties =
                                                    Klaxon().parse<CRSProperties>(it.nextObject().toJsonString())
                                            }
                                        }
                                        CRS(crsType!!, properties!!)
                                    }

                                    "features" -> it.beginArray {
                                        while (it.hasNext()) {
                                            val time = measureTime {
                                                val route = getMappedRoute(it, !parsePaths)
                                                trackInfos.add(route.trackInfo)

                                                if (writeSeparatePathFiles) {
                                                    val out =
                                                        FileOutputStream("$debugDir${route.trackInfo.objectId}.json")
                                                    val simplifiedCoordinates =
                                                        simplify(multilineToCoordinates(route.multiLineString))
                                                    val track =
                                                        Track(route.trackInfo.objectId, simplifiedCoordinates)
                                                    out.use { out.write(track.toJson().toByteArray()) }
                                                    pathsWritten++
                                                    pathSizeMap[route.trackInfo] = simplifiedCoordinates.size
                                                } else {
                                                    if (pathFOS != null && (pathIDsToWrite == null || pathIDsToWrite.contains(
                                                            route.trackInfo.objectId
                                                        ))
                                                    ) {
                                                        val simplifiedCoordinates =
                                                            simplify(multilineToCoordinates(route.multiLineString))
                                                        val track =
                                                            Track(route.trackInfo.objectId, simplifiedCoordinates)
                                                        pathFOS.write(track.toJson().toByteArray())
                                                        pathsWritten++
                                                        pathSizeMap[route.trackInfo] = simplifiedCoordinates.size

                                                        // Determine whether a "," should be added
                                                        if (pathIDsToWrite == null) {
                                                            if (it.hasNext()) {
                                                                pathFOS.write(",".toByteArray())
                                                            }
                                                        } else if (pathIDsToWrite.contains(route.trackInfo.objectId)) {
                                                            if (pathsWritten < pathIDsToWrite.size) {
                                                                pathFOS.write(",".toByteArray())
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            t = t.plus(time)
                                            if (trackInfos.size % 100 == 0) {
                                                println("- ${trackInfos.size} routes parsed in $t")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pathFOS != null) {
                println("- $pathsWritten tracks written")
                println("-- Tracks with >100 coordinates:${pathSizeMap.values.filter { it > 100 }.size}")
                println("-- Tracks with >500 coordinates:${pathSizeMap.values.filter { it > 500 }.size}")
                println("-- Tracks with >1000 coordinates:${pathSizeMap.values.filter { it > 1000 }.size}")
                println("-- Tracks with >2000 coordinates:${pathSizeMap.values.filter { it > 2000 }.size}")
                println("-- Tracks with >3000 coordinates:${pathSizeMap.values.filter { it > 3000 }.size}")
                println("-- Max:${pathSizeMap.values.maxOrNull()}, Min:${pathSizeMap.values.minOrNull()}")
            }

            if (parseTrackInfo) {
                val trackInfoOutput = FileOutputStream(TRACK_INFO_EXPORT_PATH)
                trackInfoOutput.use {
                    val writer: Writer = OutputStreamWriter(GZIPOutputStream(it), Charsets.UTF_8)
                    writer.use { w ->
                        w.write(Klaxon().toJsonString(trackInfos))
                    }
                }
            }
        }

        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
        private fun getMappedRoute(reader: JsonReader, ignorePath: Boolean): GovTrack {
            var trackInfo: TrackInfo? = null
            var multiLineString: List<CrsCoordinate> = listOf()
            reader.beginObject {
                var featuresType: String? = null

                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "type" -> featuresType = reader.nextString()
                        "properties" -> trackInfo = Klaxon().parse<TrackInfo>(reader.nextObject().toJsonString())

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
            return GovTrack(trackInfo!!, multiLineString)
        }

        private fun getPath(reader: JsonReader, isMultiLineString: Boolean, ignorePath: Boolean): List<CrsCoordinate> {
            val wgs84coordinates = mutableListOf<CrsCoordinate>()
            reader.beginArray {
                while (reader.hasNext()) {
                    if (isMultiLineString) {
                        reader.beginArray {
                            val hk1980Coordinates = getCRSCoordinates(reader, ignorePath)

                            // Removes duplicates (notes: The full path is organized as individual paths between stops.
                            // The first coordinate of the next line is the same as the last coordinate of the previous
                            // line)
                            if (wgs84coordinates.size > 0) {
                                hk1980Coordinates.removeFirst()
                            }
                            wgs84coordinates.addAll(getWgs84CRSCoordinates(hk1980Coordinates))
                        }
                    } else {
                        val hk1980Coordinates = getCRSCoordinates(reader, ignorePath)
                        wgs84coordinates.addAll(getWgs84CRSCoordinates(hk1980Coordinates))
                    }
                }
            }
            return wgs84coordinates
        }

        private fun getCRSCoordinates(reader: JsonReader, ignorePath: Boolean): MutableList<CrsCoordinate> {
            if (ignorePath) {
                while (reader.hasNext()) {
                    reader.nextArray()
                }
                return mutableListOf()
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

        private fun getWgs84CRSCoordinates(hk1980Coordinates: List<CrsCoordinate>): List<CrsCoordinate> {
            return hk1980Coordinates.parallelStream().map { hk1980Coordinate ->
                crsTransformationAdapter.transformToCoordinate(
                    hk1980Coordinate, EpsgNumber.WORLD__WGS_84__4326
                )
            }.collect(Collectors.toList())
        }

        private fun multilineToCoordinates(multiline: List<CrsCoordinate>) = multiline.map { crsCoordinate ->
            val lat = crsCoordinate.getLatitude().roundCoordinate()
            val long = crsCoordinate.getLongitude().roundCoordinate()
            listOf(lat, long)
        }
    }
}

fun main() {
    execute("Parsing trackInfo...", true) {
        TrackParser.parseFile(
            exportTrackInfoToFile = true, parsePaths = true, pathIDsToWrite = null, writeSeparatePathFiles = false
        )
    }
}