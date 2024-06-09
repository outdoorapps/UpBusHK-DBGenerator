package json_model

import com.beust.klaxon.Klaxon

data class MtrbRequestBody(val language: String, val routeName: String) {
    fun toJson() = Klaxon().toJsonString(this)
}