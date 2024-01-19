import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import data.RequestableRoute
import data.Stop


val sharedData: SharedData = SharedData()

data class SharedData(
    @Json(name = "routes") val requestableRoutes: MutableList<RequestableRoute> = mutableListOf(),
    val stops: MutableList<Stop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
