package xml_model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName

@JsonRootName("dataroot")
data class BusRouteInfoRoot(
    @JsonProperty("ROUTE") val list: List<BusRouteInfo>
)

@JsonRootName("ROUTE")
data class BusRouteInfo(
    @JsonProperty("ROUTE_ID") val routeID: Int,
    @JsonProperty("COMPANY_CODE") val companyCode: String,
    @JsonProperty("ROUTE_NAMEC") val routeNamec: String,
    @JsonProperty("ROUTE_NAMES") val routeNames: String,
    @JsonProperty("ROUTE_NAMEE") val routeNamee: String,
    @JsonProperty("ROUTE_TYPE") val routeType: Int,
    @JsonProperty("SERVICE_MODE") val serviceMode: String,
    @JsonProperty("SPECIAL_TYPE") val specialType: Int,
    @JsonProperty("JOURNEY_TIME") val journeyTime: Int,
    @JsonProperty("LOC_START_NAMEC") val locStartNamec: String,
    @JsonProperty("LOC_START_NAMES") val locStartNames: String,
    @JsonProperty("LOC_START_NAMEE") val locStartNamee: String,
    @JsonProperty("LOC_END_NAMEC") val locEndNamec: String,
    @JsonProperty("LOC_END_NAMES") val locEndNames: String,
    @JsonProperty("LOC_END_NAMEE") val locEndNamee: String,
    @JsonProperty("HYPERLINK_C") val hyperlinkC: String,
    @JsonProperty("HYPERLINK_S") val hyperlinkS: String,
    @JsonProperty("HYPERLINK_E") val hyperlinkE: String,
    @JsonProperty("FULL_FARE") val fullFare: Double,
    @JsonProperty("LAST_UPDATE_DATE") val lastUpdateDate: String
)
