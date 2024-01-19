import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import data.RequestableRoute
import data.RequestableStop


val sharedData: SharedData = SharedData()

data class SharedData(
    @Json(name = "routes") val requestableRoutes: MutableList<RequestableRoute> = mutableListOf(),
    @Json(name = "stops") val requestableStops: MutableList<RequestableStop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
