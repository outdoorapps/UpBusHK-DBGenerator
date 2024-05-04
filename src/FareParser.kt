import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper.builder
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import xml_model.BusFareRoot
import java.io.File

class FareParser {
    companion object {
        private val kotlinXmlMapper = builder().configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).defaultUseWrapper(false).build()
            .registerKotlinModule()

        fun getBusFareMap(): MutableMap<String, MutableList<Pair<Int, Double>>> {
            val file = File("resources/FARE_BUS.xml")
            var xml: String
            file.inputStream().use { input ->
                xml = input.bufferedReader().use { it.readText() }
            }
            val busFareRoot = kotlinXmlMapper.readValue<BusFareRoot>(xml)
            println("Bus fare parsed with ${busFareRoot.list.size} entries") // the first action!!!

            val busFareMap = mutableMapOf<String, MutableList<Pair<Int, Double>>>()
            busFareRoot.list.forEach { fareItem ->
                val id = "${fareItem.routeId}-${fareItem.routeSeq}"
                if (busFareMap[id] == null) {
                    busFareMap[id] = mutableListOf()
                }
                val fareList = busFareMap[id]!!
                if (fareList.none { it.first == fareItem.onSeq }) {
                    fareList.add(Pair(fareItem.onSeq, fareItem.fare))
                }
            }
            busFareMap.forEach { (_, list) -> list.sortBy { it.first } }
            return busFareMap
        }
    }
}


fun main() {
    //todo val fareMap = getBusFareMap()
}
