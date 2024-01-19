import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Utils {
    companion object {
        fun printPercentage(currentCount: Int, totalCount: Int, startTimeInMillis: Long) {
            val percentage = currentCount.toDouble() / totalCount.toDouble() * 100
            val timeElapse = (System.currentTimeMillis() - startTimeInMillis).toDuration(DurationUnit.MILLISECONDS)
            println("($currentCount/$totalCount) ${String.format("%.1f", percentage)} % in $timeElapse")
        }

        fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            val lon1Rad = Math.toRadians(lon1)
            val lon2Rad = Math.toRadians(lon2)

            val x = (lon2Rad - lon1Rad) * cos((lat1Rad + lat2Rad) / 2)
            val y = (lat2Rad - lat1Rad)
            val distance: Double = sqrt(x * x + y * y) * 6371

            return distance * 1000
        }
    }
}