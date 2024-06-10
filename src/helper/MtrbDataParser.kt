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
        private val latRegex = "22.([0-9]+)(?=(113|114)\\.)".toRegex()
        private val longRegex = "(113|114)\\.([0-9]+)(?=\\s\\p{IsHan})".toRegex()
        private val chiNameRegex = "(\\p{IsHan})+[^=A-Z]*".toRegex()

        fun parseMtrbData() {
            val file = File(MTRB_DATA_PATH)
            file.inputStream().use { input ->
                input.bufferedReader().use { buffer ->
                    buffer.forEachLine { line ->
                        if (line.startsWith("Route")) {
                            val number = mtrbRouteRegex.find(line)?.value
                            val boundText = boundRegex.find(line)?.value
                            val bound =
                                if (boundText == "Outbound" || boundText == "Single Direction") Bound.I else Bound.O

                        } else if (line.matches("^($stopIdRegex).*?".toRegex())) {
                            val items = line.split(' ')
                            val stopId = items[0]
                            val lat = items[1].toDouble()
                            val long = items[2].toDouble()
//                            val chiNameText = items[3]
//                            val engName = items[4]
//
//                            val chiName = chiNameText.replace(TSUEN_CODE, TSUEN_CHARACTER)
//                            val chiSName = ZhConverterUtil.toSimple(chiName)
//                            val stopId = stopIdRegex.find(line)?.value
//                            val lat = latRegex.find(line)?.value?.toDouble()
//                            val long = longRegex.find(line)?.value?.toDouble()
                            val chiNameText = chiNameRegex.find(line)?.value
                            val chiName = chiNameText?.trim()?.replace(TSUEN_CODE, TSUEN_CHARACTER)
                            val chiSName = ZhConverterUtil.toSimple(chiName)
                            val engName = line.substringAfter("$chiNameText").trim()

                            BusStop(
                                company = Company.MTRB,
                                stopId = stopId,
                                engName = engName,
                                chiTName = chiName!!,
                                chiSName = chiSName!!,
                                listOf(lat, long)
                            )
                            println("$stopId - $lat,$long,$chiName,$engName")
                        } else {
                            // start a now route
                        }
                    }
                }
            }
        }
    }
}

fun main() {
    MtrbDataParser.parseMtrbData()
}