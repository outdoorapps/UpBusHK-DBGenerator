import Paths.Companion.BUS_STOPS_SOURCE_PATH
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import data.GovRecordStop
import json_models.BusStopRaw
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

class Utils {
    companion object {
        fun printPercentage(currentCount: Int, totalCount: Int, startTimeInMillis: Long) {
            val percentage = currentCount.toDouble() / totalCount.toDouble() * 100
            val timeElapse = (System.currentTimeMillis() - startTimeInMillis).toDuration(DurationUnit.MILLISECONDS)
            println("($currentCount/$totalCount) ${String.format("%.1f", percentage)} % in $timeElapse")
        }

        fun distanceInMeters(latLng1: List<Double>, latLng2: List<Double>): Double {
            val lat1Rad = Math.toRadians(latLng1[0])
            val lat2Rad = Math.toRadians(latLng2[0])
            val lon1Rad = Math.toRadians(latLng1[1])
            val lon2Rad = Math.toRadians(latLng2[1])

            val x = (lon2Rad - lon1Rad) * cos((lat1Rad + lat2Rad) / 2)
            val y = (lat2Rad - lat1Rad)
            val distance: Double = sqrt(x * x + y * y) * 6371

            return distance * 1000
        }


        fun executeWithCount(description: String, action: () -> Int) {
            print(description)
            println()

            var count: Int
            val t = measureTime {
                count = action()
            }
            println("added $count in $t")
        }

        fun execute(description: String, printOnNextLine: Boolean = false, action: () -> Unit) {
            print(description)
            if (printOnNextLine) println()
            val t = measureTime { action() }
            println("Finished in $t")
        }

        fun writeToGZ(data: String, path: String) {
            val output = FileOutputStream(path)
            output.use {
                val writer = OutputStreamWriter(GZIPOutputStream(it), Charsets.UTF_8)
                writer.use { w ->
                    w.write(data)
                }
            }
        }

        fun writeToJsonFile(data: String, path: String) {
            val output = FileOutputStream(path)
            output.use {
                it.write(data.toByteArray())
            }
        }

        fun loadGovRecordStop(): List<GovRecordStop> {
            val govRecordStops = mutableListOf<GovRecordStop>()
            val crsTransformationAdapter =
                CrsTransformationAdapterCompositeFactory.createCrsTransformationFirstSuccess()
            val klaxon = Klaxon()
            val file = ZipFile(BUS_STOPS_SOURCE_PATH)
            val stream = file.getInputStream(file.entries().nextElement())
            val jsonString = stream.bufferedReader().use { it.readText() }
            val busStopFeature = klaxon.parse<BusStopRaw>(jsonString)!!.features
            busStopFeature.forEach {
                val crsCoordinate = crsTransformationAdapter.transformToCoordinate(
                    eastingNorthing(
                        it.geometry.coordinates[0].toDouble(),
                        it.geometry.coordinates[1].toDouble(),
                        EpsgNumber.CHINA__HONG_KONG__HONG_KONG_1980_GRID_SYSTEM__2326
                    ), EpsgNumber.WORLD__WGS_84__4326
                )
                govRecordStops.add(
                    GovRecordStop(
                        it.properties.stopId,
                        mutableListOf(crsCoordinate.getLatitude(), crsCoordinate.getLongitude())
                    )
                )
            }
            govRecordStops.sortBy { x -> x.stopId }
            return govRecordStops
        }
    }
}