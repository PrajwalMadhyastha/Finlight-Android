package io.pm.finlight.analyzer

/**
 * Helper data class to return 4 values (like Triple but with 4 elements)
 */
data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
