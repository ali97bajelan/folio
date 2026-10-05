package com.example.folio.data.network

import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONObject

data class PriceResult(
    val price: BigDecimal,
    val currency: String,
    val provider: String,
    val providerSymbol: String,
    val fetchedAt: Instant = Instant.now(),
)

sealed class PricingException(message: String) : Exception(message) {
    class Unavailable(message: String) : PricingException(message)
    class Invalid(message: String) : PricingException(message)
    class Unsupported(message: String) : PricingException(message)
}

object ProviderParsers {
    fun nobitex(symbol: String, body: String): PriceResult {
        val (base, quote) = marketParts(symbol)
        val rawPrice = JSONObject(body).optJSONObject("stats")?.optJSONObject("$base-$quote")?.optString("latest")
            ?: throw PricingException.Invalid("Nobitex returned an unexpected market response.")
        val price = validPrice(rawPrice, "Nobitex")
        val isRial = quote == "rls"
        return PriceResult(if (isRial) price.divide(BigDecimal.TEN) else price, if (isRial) "IRT" else quote.uppercase(), "NOBITEX", symbol.uppercase())
    }

    fun tsetmc(symbol: String, body: String): PriceResult {
        requireNumericId(symbol, "TSETMC requires a numeric InsCode, not a ticker symbol.")
        val quote = JSONObject(body).optJSONObject("closingPriceInfo")
            ?: throw PricingException.Invalid("TSETMC returned an unexpected quote response.")
        val rawPrice = quote.opt("pClosing") ?: quote.opt("pDrCotVal")
            ?: throw PricingException.Invalid("TSETMC returned an invalid closing price.")
        return PriceResult(validPrice(rawPrice.toString(), "TSETMC").divide(BigDecimal.TEN), "IRT", "TSETMC", symbol)
    }

    fun aban(symbol: String, body: String): PriceResult = aban(symbol, abanCatalogue(body))

    internal fun abanCatalogue(body: String): Map<String, JSONObject> {
        val coins = JSONObject(body).optJSONArray("data")
            ?: throw PricingException.Invalid("Aban Tether returned an unexpected coin response.")
        return buildMap {
            for (index in 0 until coins.length()) {
                val coin = coins.getJSONObject(index)
                if (coin.optBoolean("is_active")) {
                    putIfAbsent(coin.optString("symbol").uppercase(), coin)
                }
            }
        }
    }

    internal fun aban(symbol: String, catalogue: Map<String, JSONObject>): PriceResult {
        val coin = catalogue[symbol.uppercase()]
            ?: throw PricingException.Unsupported("No active Aban Tether asset exists for $symbol.")
        val buy = validPrice(coin.optString("price_buy"), "Aban Tether")
        val sell = validPrice(coin.optString("price_sell"), "Aban Tether")
        return PriceResult(buy.add(sell).divide(BigDecimal("2")), "IRT", "ABANTETHER", symbol.uppercase())
    }

    fun rahavard(symbol: String, body: String): PriceResult {
        requireNumericId(symbol, "Rahavard requires a numeric entity ID.")
        val data = JSONObject(body).optJSONObject("data")
            ?: throw PricingException.Invalid("Rahavard returned an unexpected quote response.")
        val header = data.optJSONObject("header_last_trade") ?: JSONObject()
        val trade = data.optJSONObject("last_trade") ?: JSONObject()
        val rawPrice = header.optString("real_close_price").ifBlank { trade.optString("close_price") }
        val timestamp = header.optString("end_date_time").ifBlank { trade.optString("end_date_time") }
        return PriceResult(
            validPrice(rawPrice, "Rahavard").divide(BigDecimal.TEN), "IRT", "RAHAVARD", symbol,
            runCatching { OffsetDateTime.parse(timestamp).toInstant() }.getOrElse { Instant.now() },
        )
    }

    fun marketParts(symbol: String): Pair<String, String> {
        val normalized = symbol.trim().lowercase().replace('_', '-').replace('/', '-')
        val pair = if ('-' in normalized) {
            val parts = normalized.split('-', limit = 2)
            if (parts.any(String::isBlank)) invalidPair()
            parts[0] to parts[1]
        } else {
            listOf("usdt", "irt", "rls").firstNotNullOfOrNull { suffix ->
                normalized.removeSuffix(suffix).takeIf { normalized.endsWith(suffix) && it.isNotBlank() }?.let { it to suffix }
            } ?: invalidPair()
        }
        return pair.first to if (pair.second == "irt") "rls" else pair.second
    }

    private fun validPrice(raw: String, provider: String): BigDecimal {
        val price = raw.toBigDecimalOrNull() ?: throw PricingException.Invalid("$provider returned an invalid price.")
        if (price <= BigDecimal.ZERO) throw PricingException.Invalid("$provider returned an invalid price.")
        return price
    }

    private fun requireNumericId(symbol: String, message: String) {
        if (symbol.isBlank() || !symbol.all(Char::isDigit)) throw PricingException.Unsupported(message)
    }

    private fun invalidPair(): Nothing = throw PricingException.Unsupported("Use a Nobitex pair such as BTCUSDT or USDTIRT.")
}
