package com.techrush_app.goldrate

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rates feed, scraped in CI and published as a small JSON.
 *
 * The point is that a source changing its markup can be fixed without a Play
 * release: `tools/build_rates.py` is edited, the workflow republishes, and
 * every user picks it up on their next launch. The on-device scrapers stay
 * behind this as a fallback, so the feed is a fast path rather than a single
 * point of failure — if it's unreachable, stale, or a schema the app doesn't
 * know, the app scrapes for itself exactly as before.
 *
 * Built by `.github/workflows/rates.yml`.
 */
const val RATES_FEED_URL = "https://preveenraj.github.io/goldrate-kotlin/rates.json"

/**
 * The feed layout this build understands. The publisher may add fields freely
 * (they're ignored); bumping this number is reserved for a change that would
 * make an old app read the feed *wrongly*, which must instead make it fall
 * back to scraping.
 */
private const val SUPPORTED_SCHEMA = 1

/**
 * How old a feed may be before the app stops trusting it. The workflow runs
 * twice an hour, so this survives a long run of failures while still ensuring
 * a permanently dead workflow can never pin the app to a frozen rate — it ages
 * out and the scrapers take over the same day.
 */
private const val MAX_FEED_AGE_SECONDS = 6L * 60 * 60

/**
 * Deduplicates the fetch. Gold loads on launch and silver on first tab switch;
 * without this they'd pull the same document twice. Deliberately short: this
 * is request coalescing, not a cache, and the feed only moves twice an hour.
 */
private const val CACHE_TTL_MILLIS = 60_000L

/** Both metals as published. Either may be absent if the feed is partial. */
internal data class RatesFeed(val gold: Result?, val silver: SilverResult?)

private val feedMutex = Mutex()
private var cachedFeed: RatesFeed? = null
private var cachedAtMillis = 0L

private suspend fun feed(): RatesFeed? = feedMutex.withLock {
    val now = System.currentTimeMillis()
    val cached = cachedFeed
    if (cached != null && now - cachedAtMillis < CACHE_TTL_MILLIS) return@withLock cached

    val json = fetchPageHtml(RATES_FEED_URL) ?: return@withLock null
    val parsed = parseRatesFeed(json, now / 1000)
    if (parsed != null) {
        cachedFeed = parsed
        cachedAtMillis = now
    }
    parsed
}

/** Today's gold from the feed, or null to fall through to the scrapers. */
suspend fun fetchFeedGold(): Result? = feed()?.gold

/** Today's silver from the feed, or null to fall through to the scrapers. */
suspend fun fetchFeedSilver(): SilverResult? = feed()?.silver

/**
 * Parses the published feed. [nowSeconds] is passed in rather than read here so
 * the staleness rule can be tested without waiting six hours.
 *
 * Returns null — meaning "scrape instead" — for anything the app can't fully
 * trust: malformed JSON, a schema it doesn't know, or a feed too old.
 */
internal fun parseRatesFeed(json: String, nowSeconds: Long): RatesFeed? {
    val root = try {
        JSONObject(json)
    } catch (e: Exception) {
        Log.e("ratesFeed", "Feed is not valid JSON", e)
        return null
    }

    val schema = root.optInt("schema", -1)
    if (schema != SUPPORTED_SCHEMA) {
        Log.w("ratesFeed", "Feed schema $schema is not $SUPPORTED_SCHEMA; scraping instead")
        return null
    }

    val generatedAt = root.optLong("generatedAt", 0L)
    val age = nowSeconds - generatedAt
    if (generatedAt <= 0L || age > MAX_FEED_AGE_SECONDS) {
        Log.w("ratesFeed", "Feed is ${age}s old; scraping instead")
        return null
    }
    // A feed from the future means a clock is wrong somewhere. Tolerate a small
    // skew, distrust anything beyond it.
    if (age < -MAX_FEED_AGE_SECONDS) {
        Log.w("ratesFeed", "Feed is dated ${-age}s ahead; scraping instead")
        return null
    }

    return RatesFeed(
        gold = root.optJSONObject("gold")?.let { goldFrom(it) },
        silver = root.optJSONObject("silver")?.let { silverFrom(it) },
    )
}

private fun pointsFrom(array: JSONArray?): List<RatePoint> {
    if (array == null) return emptyList()
    val out = mutableListOf<RatePoint>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val date = item.optString("date")
        val rate = item.optInt("rate", -1)
        if (date.isBlank() || rate <= 0) continue
        out.add(RatePoint(date, rate, item.stringOrNull("session")))
    }
    return out
}

/** org.json turns a JSON null into the string "null" via optString; this doesn't. */
private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun goldFrom(obj: JSONObject): Result? {
    val date = obj.stringOrNull("date") ?: return null
    val rate = obj.optInt("rate", -1)
    val history = pointsFrom(obj.optJSONArray("history"))
    if (rate <= 0 || history.isEmpty()) {
        Log.w("ratesFeed", "Gold block incomplete; scraping instead")
        return null
    }
    val latest = history.last()
    return Result(
        rate = rate,
        date = date,
        session = obj.stringOrNull("session"),
        dayStatus = getDayStatus(date),
        change = obj.optInt("change", 0),
        high = history.maxByOrNull { it.rate } ?: latest,
        low = history.minByOrNull { it.rate } ?: latest,
        history = history,
        periodLabel = obj.stringOrNull("periodLabel") ?: "Month",
    )
}

private fun silverFrom(obj: JSONObject): SilverResult? {
    val date = obj.stringOrNull("date") ?: return null
    val perKg = obj.optInt("perKg", -1)
    val history = pointsFrom(obj.optJSONArray("history"))
    if (perKg <= 0 || history.isEmpty()) {
        Log.w("ratesFeed", "Silver block incomplete; scraping instead")
        return null
    }
    return SilverResult(
        perKg = perKg,
        date = date,
        dayStatus = getDayStatus(date),
        change = obj.optInt("change", 0),
        history = history,
        months = monthsFrom(obj.optJSONArray("months")),
        periodLabel = obj.stringOrNull("periodLabel") ?: "10-Day",
    )
}

private fun monthsFrom(array: JSONArray?): List<SilverMonth> {
    if (array == null) return emptyList()
    val out = mutableListOf<SilverMonth>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val label = item.stringOrNull("label") ?: continue
        out.add(
            SilverMonth(
                label = label,
                open = item.optInt("open", 0),
                close = item.optInt("close", 0),
                high = item.optInt("high", 0),
                low = item.optInt("low", 0),
                trendPct = item.optDouble("trendPct", 0.0),
            )
        )
    }
    return out
}
