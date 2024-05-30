package util

import Main.Companion.COMPRESS_TO_XZ
import com.beust.klaxon.Klaxon
import com.programmerare.crsConstants.constantsByAreaNameNumber.v10_027.EpsgNumber
import com.programmerare.crsTransformations.compositeTransformations.CrsTransformationAdapterCompositeFactory
import com.programmerare.crsTransformations.coordinate.eastingNorthing
import data.CompanyBusData
import data.GovStop
import json_model.GovStopCollection
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import util.Paths.Companion.ARCHIVE_NAME
import util.Paths.Companion.BUS_COMPANY_DATA_EXPORT_PATH
import util.Paths.Companion.BUS_STOPS_GEOJSON_PATH
import util.Paths.Companion.DB_PATHS_EXPORT_PATH
import util.Paths.Companion.DB_ROUTES_STOPS_EXPORT_PATH
import util.Paths.Companion.resourcesDir
import java.io.*
import java.math.RoundingMode
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

class Utils {
    companion object {
        val intermediates = listOf(DB_ROUTES_STOPS_EXPORT_PATH, DB_PATHS_EXPORT_PATH)

        fun printPercentage(currentCount: Int, totalCount: Int, startTimeInMillis: Long) {
            val percentage = currentCount.toDouble() / totalCount.toDouble() * 100
            val timeElapse = (System.currentTimeMillis() - startTimeInMillis).toDuration(DurationUnit.MILLISECONDS)
            println("($currentCount/$totalCount) ${String.format("%.1f", percentage)} % in $timeElapse")
        }

        fun distanceInMeters(coordinate1: List<Double>, coordinate2: List<Double>): Double {
            val lat1Rad = Math.toRadians(coordinate1[0])
            val lat2Rad = Math.toRadians(coordinate2[0])
            val lon1Rad = Math.toRadians(coordinate1[1])
            val lon2Rad = Math.toRadians(coordinate2[1])

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
            println("- added $count in $t")
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

        fun loadCompanyBusData(): CompanyBusData {
            val companyBusData = CompanyBusData()
            val dbFile = File(BUS_COMPANY_DATA_EXPORT_PATH)
            val dbStream = GZIPInputStream(dbFile.inputStream())
            val jsonString = dbStream.bufferedReader().use { it.readText() }
            val data = Klaxon().parse<CompanyBusData>(jsonString)
            if (data != null) {
                companyBusData.companyBusRoutes.addAll(data.companyBusRoutes)
                companyBusData.busStops.addAll(data.busStops)
            }
            return companyBusData
        }

        fun loadGovBusStops(): List<GovStop> {
            val govStops = mutableListOf<GovStop>()
            val crsTransformationAdapter =
                CrsTransformationAdapterCompositeFactory.createCrsTransformationFirstSuccess()
            val file = ZipFile(BUS_STOPS_GEOJSON_PATH)
            val jsonString = file.getInputStream(file.entries().nextElement()).use { input ->
                input.bufferedReader().use { it.readText() }
            }

            val feature = Klaxon().parse<GovStopCollection>(jsonString)!!.features
            feature.forEach {
                val crsCoordinate = crsTransformationAdapter.transformToCoordinate(
                    eastingNorthing(
                        it.geometry.hk1980Coordinates[0].toDouble(),
                        it.geometry.hk1980Coordinates[1].toDouble(),
                        EpsgNumber.CHINA__HONG_KONG__HONG_KONG_1980_GRID_SYSTEM__2326
                    ), EpsgNumber.WORLD__WGS_84__4326
                )
                govStops.add(
                    GovStop(
                        it.properties.stopId, mutableListOf(crsCoordinate.getLatitude(), crsCoordinate.getLongitude())
                    )
                )
            }
            govStops.sortBy { x -> x.stopId }
            return govStops
        }

        fun getCompanies(companyCode: String): Set<Company> =
            companyCode.split("+").map { Company.fromValue(it) }.toSet()

        fun writeToArchive(files: List<String>, compressToXZ: Boolean, deleteSource: Boolean) {
            execute("Compressing files to archive...") {
                val path = getArchivePath()
                val output = FileOutputStream(path)
                val compressionStream =
                    if (compressToXZ) XZOutputStream(output, LZMA2Options()) else GZIPOutputStream(output)
                TarArchiveOutputStream(compressionStream).use {
                    files.forEach { path ->
                        val file = File(path)
                        FileInputStream(file).use { input ->
                            val entry = TarArchiveEntry(file.name)
                            entry.size = file.length()
                            it.putArchiveEntry(entry)
                            input.copyTo(it)
                            it.closeArchiveEntry()
                        }
                    }
                }
            }
            if (deleteSource) execute("Cleaning up intermediates...") { files.forEach { File(it).delete() } }
        }

        fun writeToCSV(filePath: String, coordinates: List<List<Double>>) {
            val output = FileOutputStream(filePath)
            output.use {
                val writer: Writer = OutputStreamWriter(output, Charsets.UTF_8)
                writer.use { w ->
                    w.write("WKT,name,description\n\"LINESTRING (")
                    for (i in coordinates.indices) {
                        w.write("${coordinates[i][1]} ${coordinates[i][0]}")
                        if (i != coordinates.size - 1) {
                            w.write(", ")
                        }
                    }
                    w.write(")\",Line 1,")
                }
            }
        }

        fun getArchivePath(): String = "$resourcesDir$ARCHIVE_NAME.tar" + if (COMPRESS_TO_XZ) ".xz" else ".gz"

        fun Double.roundCoordinate(): Double = this.toBigDecimal().setScale(5, RoundingMode.HALF_EVEN).toDouble()

        private fun String.trimIdeographicSpace(): String = this.replace("\u3000", "")

        private fun String.toHalfWidth(): String {
            val halfWidth = StringBuilder()
            this.map { it.toHalfWidth() }.forEach { halfWidth.append(it) }
            return halfWidth.toString()
        }

        private fun Char.toHalfWidth(): Char = if (this.code in 0xff01..0xff5e) {
            (this.code + 0x20 and 0xFF).toChar()
        } else {
            this
        }

        // Standardize Chinese stop names (Minibus)
        // 1. Remove ideographic space "\u3000"
        // 2. fullWidth characters to halfWidth
        // 3. No whitespace after "近" for Chinese locations (e.g. 近 富豪苑 to 近富豪苑)
        // 4. Add whitespace after "近" for English locations (e.g. 近One Hennessy to 近 One Hennessy) except for 近M+博物館
        // 5. Remove in-between whitespace (第 5 號 to 第5號)
        // 6. Trim preceding and trailing whitespace
        fun String.standardizeChiStopName(): String =
            this.trimIdeographicSpace().replace(regex = Regex("[Ａ-Ｚａ-ｚ０-９－]")) { a -> a.value.toHalfWidth() }
                .replace(Regex("\\s*,\\s*|，"), ", ")
                .replace(Regex("近\\s*\\p{IsHan}")) { a -> a.value.replace(Regex("\\s*"), "") }
                .replace(Regex("近[A-Za-z]{3,}")) { x -> "${x.value[0]} ${x.value.removePrefix("近")}" }
                .replace(Regex("\\p{IsHan}\\s*[A-Za-z0-9-]+\\s*\\p{IsHan}")) { x ->
                    x.value.replace(Regex("\\s*"), "")
                }.trim()
    }
}