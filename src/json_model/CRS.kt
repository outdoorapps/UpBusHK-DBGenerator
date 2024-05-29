package json_model

data class CRS (
    val type: String,
    val properties: CRSProperties
)

data class CRSProperties (
    val name: String
)