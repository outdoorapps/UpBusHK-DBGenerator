package helper

import com.github.houbb.opencc4j.util.ZhConverterUtil
import data.BusStop
import util.Bound
import util.Company
import util.Paths.Companion.MTRB_DATA_PATH
import java.io.File

class MtrbDataParser {
    companion object {
        private const val TSUEN_CODE = "&#37032;"
        private const val TSUEN_CHARACTER = "邨"

        private val mtrbRouteRegex = "K[0-9]+[A-Z]?|506".toRegex()
        private val boundRegex = "(?<=\\().+?(?=\\))".toRegex()
        private val stopIdRegex = "^($mtrbRouteRegex)-[a-z]?[A-Z][0-9]{3}".toRegex()
        private val chiNameRegex = "(\\p{IsHan})+[^=A-Z]*".toRegex()

        // Map: routeNumber -> bound -> stops
        fun parseMtrbData(): Map<String, Map<Bound, List<BusStop>>> {
            val routeMap = mutableMapOf<String, MutableMap<Bound, MutableList<BusStop>>>()
            val file = File(MTRB_DATA_PATH)
            var number: String? = null
            var bound: Bound? = null
            file.inputStream().use { input ->
                input.bufferedReader().use { buffer ->
                    buffer.forEachLine { line ->
                        if (line.startsWith("Route")) {
                            number = mtrbRouteRegex.find(line)?.value
                            bound = when (boundRegex.find(line)?.value) {
                                null -> null
                                "Outbound", "Single Direction" -> Bound.O
                                else -> Bound.I
                            }
                            if (number != null && bound != null) {
                                if (routeMap[number] == null) {
                                    routeMap[number!!] = mutableMapOf()
                                }
                                if (routeMap[number]!![bound] == null) {
                                    routeMap[number]!![bound!!] = mutableListOf()
                                }
                            }
                        } else if (line.matches("^($stopIdRegex).*?".toRegex())) {
                            val items = line.split(' ')
                            val stopId = items[0]
                            val lat = items[1].toDouble()
                            val long = items[2].toDouble()
                            val chiNameText = chiNameRegex.find(line)?.value
                            val chiName = chiNameText?.trim()?.replace(TSUEN_CODE, TSUEN_CHARACTER)
                            val chiSName = ZhConverterUtil.toSimple(chiName)
                            val engName = line.substringAfter("$chiNameText").trim()
                            val stop = BusStop(
                                company = Company.MTRB,
                                stopId = stopId,
                                engName = engName,
                                chiTName = chiName!!,
                                chiSName = chiSName!!,
                                listOf(lat, long)
                            )
                            if (number != null && bound != null) {
                                routeMap[number]!![bound!!]!!.add(stop)
                            }
                        }
                    }
                }
            }
            return routeMap
        }
    }
}

fun main() {
    MtrbDataParser.parseMtrbData()
}