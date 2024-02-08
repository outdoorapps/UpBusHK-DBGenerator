package utils

import com.beust.klaxon.Klaxon
import data.BusTrack
import mil.nga.sf.Point
import mil.nga.sf.util.GeometryUtils.perpendicularDistance
import utils.Paths.Companion.tempDir
import utils.RamerDouglasPeucker.Companion.simplify
import utils.Utils.Companion.writeToCSV
import java.io.File

data class Coordinate(val longitude: Double, val latitude: Double) {
    fun toJson() = Klaxon().toJsonString(this)
}

/**
 * Source https://gist.github.com/ghiermann/ed692322088bb39166a669a8ed3a6d14
 */
class RamerDouglasPeucker {
    companion object {
        private const val DEFAULT_EPSILON = 0.01 / 1000000
        private fun pointFromCoord(coord: List<Double>): Point = Point(coord[1], coord[0])

        fun simplify(coords: List<List<Double>>, epsilon: Double = DEFAULT_EPSILON): List<List<Double>> {
            // Find the point with the maximum distance
            var dmax = 0.0
            var index = 0
            val end = coords.size

            for (i in 1..(end - 2)) {
                val d = perpendicularDistance(
                    pointFromCoord(coords[i]), pointFromCoord(coords[0]), pointFromCoord(coords[end - 1])
                )
                if (d > dmax) {
                    index = i
                    dmax = d
                }
            }
            // If max distance is greater than epsilon, recursively simplify
            return if (dmax > epsilon) {
                // Recursive call
                val recResults1: List<List<Double>> = simplify(coords.subList(0, index + 1), epsilon)
                val recResults2: List<List<Double>> = simplify(coords.subList(index, end), epsilon)

                // Build the result list
                listOf(recResults1.subList(0, recResults1.lastIndex), recResults2).flatMap { it.toList() }
            } else {
                listOf(coords[0], coords[end - 1])
            }
        }
    }
}

fun main() {
    val num = 1339
    val file = File("$tempDir$num.json")
    val busTrack = Klaxon().parse<BusTrack>(file.readText())!!
    Utils.execute("Simplifying path - ${busTrack.latLngCoords.size} point...") {
        val result = simplify(busTrack.latLngCoords)
        writeToCSV("resources/csv/$num.csv", result)
        writeToCSV("resources/csv/$num-ori.csv", busTrack.latLngCoords)
        println(result.size)
    }
}