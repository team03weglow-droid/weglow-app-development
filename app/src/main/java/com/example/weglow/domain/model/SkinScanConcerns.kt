package com.example.weglow.domain.model

/** Detection counts from one completed acne scan. Counts are ranking signals, not severity. */
data class SkinScanConcerns(val counts: Map<String, Int>) {
    val active: Map<String, Int> get() = counts.filterValues { it > 0 }
}
