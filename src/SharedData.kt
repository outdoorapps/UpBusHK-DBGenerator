import com.beust.klaxon.Klaxon
import data.Route
import data.Stop


val sharedData: SharedData = SharedData()

data class SharedData(
    val routes: MutableList<Route> = mutableListOf(), val stops: MutableList<Stop> = mutableListOf()
) {
    fun toJson() = Klaxon().toJsonString(this)
}
