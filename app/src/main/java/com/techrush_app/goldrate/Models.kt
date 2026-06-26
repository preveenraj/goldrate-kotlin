package com.techrush_app.goldrate

/** A single dated rate reading from the monthly table. */
data class RatePoint(
    val date: String,
    val rate: Int,
    val session: String? = null,
)

/** The full set of data shown on the screen, derived from the monthly table. */
data class Result(
    val rate: Int,
    val date: String,
    val session: String?,
    val dayStatus: String,
    val change: Int,
    val high: RatePoint,
    val low: RatePoint,
    val history: List<Int>,
)
