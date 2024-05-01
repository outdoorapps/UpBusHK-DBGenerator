package json_models

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon

private val klaxon = Klaxon()

data class MinibusRouteInfoResponse(
    val type: String,
    val version: String,

    @Json(name = "generated_timestamp")
    val generatedTimestamp: String,

    val data: List<Datum>
) {
     fun toJson() = klaxon.toJsonString(this)

    companion object {
         fun fromJson(json: String) = klaxon.parse<MinibusRouteInfoResponse>(json)
    }
}

data class Datum(
    @Json(name = "route_id")
    val routeID: Int,

    val region: String,

    @Json(name = "route_code")
    val routeCode: String,

    @Json(name = "description_tc")
    val descriptionTc: String,

    @Json(name = "description_sc")
    val descriptionSc: String,

    @Json(name = "description_en")
    val descriptionEn: String,

    val directions: List<Direction>,

    @Json(name = "data_timestamp")
    val dataTimestamp: String
)

data class Direction(
    @Json(name = "route_seq")
    val routeSeq: Int,

    @Json(name = "orig_tc")
    val origTc: String,

    @Json(name = "orig_sc")
    val origSc: String,

    @Json(name = "orig_en")
    val origEn: String,

    @Json(name = "dest_tc")
    val destTc: String,

    @Json(name = "dest_sc")
    val destSc: String,

    @Json(name = "dest_en")
    val destEn: String,

    @Json(name = "remarks_tc")
    val remarksTc: Any? = null,

    @Json(name = "remarks_sc")
    val remarksSc: Any? = null,

    @Json(name = "remarks_en")
    val remarksEn: Any? = null,

    val headways: List<Headway>,

    @Json(name = "data_timestamp")
    val dataTimestamp: String
)

data class Headway(
    val weekdays: List<Boolean>,

    @Json(name = "public_holiday")
    val publicHoliday: Boolean,

    @Json(name = "headway_seq")
    val headwaySeq: Int,

    @Json(name = "start_time")
    val startTime: String,

    @Json(name = "end_time")
    val endTime: String,

    val frequency: Int,

    @Json(name = "frequency_upper")
    val frequencyUpper: Any? = null
)
