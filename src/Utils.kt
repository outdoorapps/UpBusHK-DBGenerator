import data.LatLng
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

        fun distanceInMeters(latLng1: LatLng, latLng2: LatLng): Double {
            val lat1Rad = Math.toRadians(latLng1.lat)
            val lat2Rad = Math.toRadians(latLng2.lat)
            val lon1Rad = Math.toRadians(latLng1.long)
            val lon2Rad = Math.toRadians(latLng2.long)

            val x = (lon2Rad - lon1Rad) * cos((lat1Rad + lat2Rad) / 2)
            val y = (lat2Rad - lat1Rad)
            val distance: Double = sqrt(x * x + y * y) * 6371

            return distance * 1000
        }
    }
}