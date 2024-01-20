class PatchData {
    companion object{
        // "376C4835851621D4" to "2560149CE75EABA0" TUEN MUN ROAD BBI (A6)
        // "B7A9E1A243516288" to "E5421509D8FC00AF" SHEK MUN ESTATE BUS TERMINUS
        // "E3B8D0FF5C269463" <- belong to an obsolete route A41,O,serviceType=5
        // "3236114A2BB68ACC" <- belong to obsolete routes A41,I,serviceType=5,6
        // "93BA278DCD263EF8" <- belong to obsolete routes A41,I,serviceType=5,6
        val stopPatchMap = mapOf(
            "376C4835851621D4" to "2560149CE75EABA0",
            "B7A9E1A243516288" to "E5421509D8FC00AF"
        )

        //val obsoleteRoute = listOf("")
    }
}