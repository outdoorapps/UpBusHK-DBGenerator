package util

enum class Company(val value: String) {
    KMB("KMB"), LWB("LWB"), CTB("CTB"), NLB("NLB"), MTRB("MTRB");

    companion object {
        fun fromValue(value: String): Company = when (value) {
            "KMB" -> KMB
            "LWB" -> LWB
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

enum class Region(val value: String) {
    HKI("HKI"), KLN("KLN"), NT("NT");

    companion object {
        fun fromValue(value: String): Region = when (value) {
            "HKI" -> HKI
            "KLN" -> KLN
            "NT" -> NT
            else -> throw IllegalArgumentException()
        }
    }
}
