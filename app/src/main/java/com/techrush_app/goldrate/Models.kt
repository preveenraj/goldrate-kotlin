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
    val history: List<RatePoint>,
    /**
     * The window [high] and [low] cover, for their card labels. The primary
     * source publishes a full month; the backup only ten days, and saying
     * "Month High" over ten days would overstate it.
     */
    val periodLabel: String = "Month",
)

/**
 * Gold purity the dashboard is showing. The scraped source quotes 22K (916);
 * 24K is the same metal at full fineness, so it scales by 24/22 — the ratio
 * every Kerala jeweller and rate aggregator uses.
 */
enum class Purity(val label: String, val caratLabel: String, val factor: Double) {
    K22("22K", "22 Carat · 916", 1.0),
    K24("24K", "24 Carat · 999", 24.0 / 22.0);

    /** Converts a scraped 22K rupee figure into this purity. */
    fun applyTo(rate22k: Int): Int = kotlin.math.round(rate22k * factor).toInt()
}

/** One month's silver movement, as published on the source's history accordion. */
data class SilverMonth(
    val label: String,
    val open: Int,
    val close: Int,
    val high: Int,
    val low: Int,
    val trendPct: Double,
)

/**
 * Today's silver picture. Rates are held per kilogram (the unit silver is
 * actually quoted and published in); per-gram figures are derived for display.
 */
data class SilverResult(
    val perKg: Int,
    val date: String,
    val dayStatus: String,
    val change: Int,
    val history: List<RatePoint>,
    val months: List<SilverMonth>,
    /**
     * The window [history] covers, for the high/low card labels. The primary
     * source publishes ten days; the backup only today and yesterday.
     */
    val periodLabel: String = "10-Day",
) {
    val perGram: Int get() = kotlin.math.round(perKg / 1000.0).toInt()
    val changePerGram: Int get() = kotlin.math.round(change / 1000.0).toInt()
}

/** Rescales a scraped 22K series to another purity. Identity for 22K itself. */
fun List<RatePoint>.at(purity: Purity): List<RatePoint> =
    if (purity == Purity.K22) this else map { it.copy(rate = purity.applyTo(it.rate)) }

/**
 * Rescales a whole 22K reading to another purity, so every figure on the screen
 * — the hero, the stats and the chart — moves together when the toggle flips.
 */
fun Result.at(purity: Purity): Result = if (purity == Purity.K22) this else copy(
    rate = purity.applyTo(rate),
    change = purity.applyTo(change),
    high = high.copy(rate = purity.applyTo(high.rate)),
    low = low.copy(rate = purity.applyTo(low.rate)),
    history = history.at(purity),
)
