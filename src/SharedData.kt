import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import data.Route
import data.Stop
import data.TestData
import kotlinx.coroutines.sync.Mutex


val testData: TestData = TestData(mutableListOf())
val sharedData: SharedData = SharedData()

data class SharedData(
    val routes: MutableList<Route> = mutableListOf(),
    val stops: MutableList<Stop> = mutableListOf()
) {
    companion object {
        @Json(ignored = true)
        val mutex = Mutex()
        fun toJson() = Klaxon().toJsonString(this)
    }
}
