package com.dmdbrands.gurus.weight.features.myKids

/**
 * True when [candidate] matches any name in [siblingNames] — trimmed and case-insensitive — i.e.
 * a duplicate baby name on the account. [siblingNames] must exclude the baby being edited so
 * re-saving its own unchanged name never self-flags (MOB-1476).
 */
fun isDuplicateBabyName(candidate: String, siblingNames: List<String>): Boolean {
    val normalized = siblingNames.map { it.trim().lowercase() }
    return candidate.trim().lowercase() in normalized
}
