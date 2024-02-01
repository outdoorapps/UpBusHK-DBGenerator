package utils

import com.beust.klaxon.Klaxon
import data.Path
import mil.nga.sf.Point
import mil.nga.sf.util.GeometryUtils.perpendicularDistance
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

        private fun pointFromCoordinate(coordinate: Coordinate): Point =
            Point(Point(coordinate.longitude, coordinate.latitude))
        fun simplify(points: List<Coordinate>, epsilon: Double): List<Coordinate> {
            // Find the point with the maximum distance
            var dmax = 0.0
            var index = 0
            val end = points.size

            for (i in 1..(end - 2)) {
                val d = perpendicularDistance(
                    pointFromCoordinate(points[i]),
                    pointFromCoordinate(points[0]),
                    pointFromCoordinate(points[end - 1])
                )
                if (d > dmax) {
                    index = i
                    dmax = d
                }
            }
            // If max distance is greater than epsilon, recursively simplify
            return if (dmax > epsilon) {
                // Recursive call
                val recResults1: List<Coordinate> = simplify(points.subList(0, index + 1), epsilon)
                val recResults2: List<Coordinate> = simplify(points.subList(index, end), epsilon)

                // Build the result list
                listOf(recResults1.subList(0, recResults1.lastIndex), recResults2).flatMap { it.toList() }
            } else {
                listOf(points[0], points[end - 1])
            }
        }
    }
}

fun main() {
    val num = 976
    val file = File("resources/temp/$num.json")
    val path = Klaxon().parse<Path>(file.readText())!!
    Utils.execute("Simplifying path - ${path.coords.size} point...") {
        val result = simplify(path.coords.map { Coordinate(it[1], it[0]) }, 0.01 / 1000000)
        writeToCSV("resources/csv/$num.csv", result.map { listOf(it.latitude, it.longitude) })
        // writeToCSV("resources/csv/$num-ori.csv", path.coords)
        println(result.size)
    }
}