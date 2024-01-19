enum class Company(val value: String) {
    KMB("KMB"), CTB("CTB"), NLB("NLB"), MTRB("MTRB");

    companion object {
        fun fromValue(value: String): Company = when (value) {
            "KMB", "LWB" -> KMB
            "CTB" -> CTB
            "NLB" -> NLB
            "MTRB" -> MTRB
            else -> throw IllegalArgumentException()
        }
    }
}

enum class Bound(val value: String) {
    O("O"), I("I");

    companion object {
        fun fromValue(value: String): Bound = when (value) {
            "O" -> O
            "I" -> I
            else -> throw IllegalArgumentException()
        }
    }
}
