import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import data.Route
import data.Stop
import kotlinx.coroutines.sync.Mutex

class SharedData {
    companion object{
        val routes: MutableList<Route> = mutableListOf()
        val stops: MutableList<Stop> = mutableListOf()

        @Json(ignored = true)
        val mutex = Mutex()
        fun toJson() = Klaxon().toJsonString(this)
    }
}
