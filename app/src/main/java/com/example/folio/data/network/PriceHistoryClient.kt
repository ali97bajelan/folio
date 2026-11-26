package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import com.example.folio.data.widget.MarketKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** One daily point returned by a market provider. */
data class PriceHistoryPoint(
    val time: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal? = null,
)

/**
 * Fetches daily OHLCV history for providers with public history endpoints.
 * Dates are inclusive and the result is ordered oldest to newest for charting.
 */
class PriceHistoryClient internal constructor(
    private val loadBody: suspend (provider: String, url: String) -> String,
) {
    constructor() : this(::defaultQuoteRequestBody)

    suspend fun fetchDaily(
        key: MarketKey,
        from: LocalDate,
        to: LocalDate = LocalDate.now(ZoneId.of("UTC")),
    ): List<PriceHistoryPoint> = withContext(Dispatchers.IO) {
        require(!from.isAfter(to)) { "Start date must be on or before end date." }
        val canonical = MarketQuoteClient().canonicalKey(key.provider, key.providerSymbol)
        val (url, parser) = when (canonical.provider) {
            PortfolioCodes.NOBITEX -> nobitexRequest(canonical, from, to)
            PortfolioCodes.TSETMC -> tsetmcRequest(canonical, from, to)
            else -> throw PricingException.Unsupported(
                "Historical prices are not available for ${canonical.provider} yet.",
            )
        }
        parser(loadBody(canonical.provider, url))
            .filter { point ->
                val date = point.time.atZone(ZoneId.of("UTC")).toLocalDate()
                date >= from && date <= to
            }
            .sortedBy(PriceHistoryPoint::time)
    }

    private fun nobitexRequest(
        key: MarketKey,
        from: LocalDate,
        to: LocalDate,
    ): Pair<String, (String) -> List<PriceHistoryPoint>> {
        val (base, quote) = ProviderParsers.marketParts(key.providerSymbol)
        val start = from.atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
        val end = to.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toEpochSecond() - 1
        val url = "https://apiv2.nobitex.ir/market/udf/history".toHttpUrl().newBuilder()
            .addQueryParameter("symbol", "${base.uppercase()}${quote.uppercase()}")
            .addQueryParameter("resolution", "D")
            .addQueryParameter("from", start.toString())
            .addQueryParameter("to", end.toString())
            .build().toString()
        return url to ::parseNobitex
    }

    private fun tsetmcRequest(
        key: MarketKey,
        from: LocalDate,
        to: LocalDate,
    ): Pair<String, (String) -> List<PriceHistoryPoint>> {
        val days = java.time.temporal.ChronoUnit.DAYS.between(from, to).coerceAtLeast(1)
        val url = "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceDailyList/${key.providerSymbol}/$days"
        return url to ::parseTsetmc
    }

    private fun parseNobitex(body: String): List<PriceHistoryPoint> {
        val json = JSONObject(body)
        if (json.optString("s") != "ok") {
            throw PricingException.Unavailable("Nobitex returned no historical prices.")
        }
        val times = json.optJSONArray("t") ?: return emptyList()
        val opens = json.optJSONArray("o") ?: return emptyList()
        val highs = json.optJSONArray("h") ?: return emptyList()
        val lows = json.optJSONArray("l") ?: return emptyList()
        val closes = json.optJSONArray("c") ?: return emptyList()
        val volumes = json.optJSONArray("v")
        val size = minOf(times.length(), opens.length(), highs.length(), lows.length(), closes.length())
        return (0 until size).mapNotNull { index ->
            val open = opens.optString(index).toBigDecimalOrNull() ?: return@mapNotNull null
            val high = highs.optString(index).toBigDecimalOrNull() ?: return@mapNotNull null
            val low = lows.optString(index).toBigDecimalOrNull() ?: return@mapNotNull null
            val close = closes.optString(index).toBigDecimalOrNull() ?: return@mapNotNull null
            PriceHistoryPoint(
                time = Instant.ofEpochSecond(times.optLong(index)),
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volumes?.optString(index)?.toBigDecimalOrNull(),
            )
        }
    }

    private fun parseTsetmc(body: String): List<PriceHistoryPoint> {
        val records = JSONObject(body).optJSONArray("closingPriceDaily")
            ?: throw PricingException.Invalid("TSETMC returned an unexpected history response.")
        val dateFormat = DateTimeFormatter.BASIC_ISO_DATE
        return (0 until records.length()).mapNotNull { index ->
            val item = records.optJSONObject(index) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(item.optString("dEven"), dateFormat) }.getOrNull()
                ?: return@mapNotNull null
            val close = item.optString("pClosing").toBigDecimalOrNull() ?: return@mapNotNull null
            val open = item.optString("priceFirst").toBigDecimalOrNull() ?: close
            val high = item.optString("priceMax").toBigDecimalOrNull() ?: close
            val low = item.optString("priceMin").toBigDecimalOrNull() ?: close
            PriceHistoryPoint(
                time = date.atStartOfDay(ZoneId.of("UTC")).toInstant(),
                open = open.divide(BigDecimal.TEN),
                high = high.divide(BigDecimal.TEN),
                low = low.divide(BigDecimal.TEN),
                close = close.divide(BigDecimal.TEN),
                volume = item.optString("qTotTran5J").toBigDecimalOrNull(),
            )
        }
    }
}
