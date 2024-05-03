package data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName

@JsonRootName("dataroot")
data class BusFareRoot(@JsonProperty("FARE") val list: List<BusFare>)

@JsonRootName("FARE")
data class BusFare(
    @JsonProperty("ROUTE_ID") val routeId: Int,
    @JsonProperty("ROUTE_SEQ") val routeSeq: Int, // Can only be 1 or 2
    @JsonProperty("ON_SEQ") val onSeq: Int,
    @JsonProperty("OFF_SEQ") val offSeq: Int,
    @JsonProperty("PRICE") val fare: Double,
    @JsonProperty("LAST_UPDATE_DATE") val lastUpdate: String //Date
)