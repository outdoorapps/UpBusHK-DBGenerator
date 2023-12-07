package data

import Bound
import Company

data class Route(
    val company: Company,
    val number: String,
    val bound: Bound,
    val originEn: String,
    val originChiT: String,
    val originChiS: String,
    val destEn: String,
    val destChiT: String,
    val destChiS: String
) {
}