package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import com.example.folio.data.widget.MarketKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val quoteHttpClient = OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

private suspend fun defaultQuoteRequestBody(provider: String, url: String): String {
    val request = Request.Builder()
        .url(url)
        .header(
            "Accept",
            if (provider == PortfolioCodes.RAHAVARD) "application/json, text/plain, */*" else "application/json",
        )
        .header(
            "User-Agent",
            when (provider) {
                PortfolioCodes.NOBITEX -> "Mozilla/5.0 (Android; Folio)"
                PortfolioCodes.RAHAVARD -> "Mozilla/5.0 (compatible; AssetsDashboard/1.0)"
                else -> "Folio Android"
            },
        )
        .apply {
            when (provider) {
                PortfolioCodes.NOBITEX -> {
                    header("Referer", "https://nobitex.ir/")
                    header("Origin", "https://nobitex.ir")
                    header("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
                    header("Pragma", "no-cache")
                }
                PortfolioCodes.RAHAVARD -> {
                    header("Referer", "https://rahavard365.com/")
                    header("Application-Name", "rahavard")
                    header("Platform", "web")
                }
            }
        }
        .build()
    return quoteHttpClient.newCall(request).awaitBody()
}

/** Shared quote transport for portfolio refreshes and widget-only markets. */
class MarketQuoteClient internal constructor(
    private val loadBody: suspend (provider: String, url: String) -> String,
) {
    constructor() : this(::defaultQuoteRequestBody)

    fun canonicalKey(provider: String, providerSymbol: String): MarketKey {
        val normalizedProvider = provider.trim().uppercase()
        val symbol = providerSymbol.trim()
        return when (normalizedProvider) {
            PortfolioCodes.NOBITEX -> {
                val (base, quote) = ProviderParsers.marketParts(symbol)
                MarketKey(normalizedProvider, "${base.uppercase()}-${quote.uppercase()}")
            }

            PortfolioCodes.ABANTETHER -> {
                if (symbol.isBlank()) throw PricingException.Unsupported("Aban Tether requires a market symbol.")
                MarketKey(normalizedProvider, symbol.uppercase())
            }

            PortfolioCodes.TSETMC, PortfolioCodes.RAHAVARD -> {
                if (symbol.isBlank() || !symbol.all(Char::isDigit)) {
                    val message = if (normalizedProvider == PortfolioCodes.TSETMC) {
                        "TSETMC requires a numeric InsCode, not a ticker symbol."
                    } else {
                        "Rahavard requires a numeric entity ID."
                    }
                    throw PricingException.Unsupported(message)
                }
                // Keep IDs as text: converting to a number would strip leading zeroes.
                MarketKey(normalizedProvider, symbol)
            }

            else -> throw PricingException.Unsupported("Unsupported price provider: $normalizedProvider")
        }
    }

    suspend fun fetch(key: MarketKey): PriceResult {
        val canonical = canonicalKey(key.provider, key.providerSymbol)
        return fetchAll(setOf(canonical)).getValue(canonical).getOrThrow()
    }

    /** Fetches every key independently while sharing Aban Tether's catalogue request. */
    suspend fun fetchAll(keys: Set<MarketKey>): Map<MarketKey, Result<PriceResult>> =
        withContext(Dispatchers.IO) { fetchAllOnIo(keys) }

    private suspend fun fetchAllOnIo(keys: Set<MarketKey>): Map<MarketKey, Result<PriceResult>> {
        val results = LinkedHashMap<MarketKey, Result<PriceResult>>()
        val canonicalKeys = LinkedHashSet<MarketKey>()
        keys.forEach { key ->
            try {
                canonicalKeys += canonicalKey(key.provider, key.providerSymbol)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // A malformed market must not prevent independent keys from refreshing.
                results[key] = Result.failure(error)
            }
        }
        if (canonicalKeys.isEmpty()) return results

        val abanKeys = canonicalKeys.filter { it.provider == PortfolioCodes.ABANTETHER }
        if (abanKeys.isNotEmpty()) {
            val catalogue = capture {
                ProviderParsers.abanCatalogue(loadBody(PortfolioCodes.ABANTETHER, ABAN_CATALOGUE_URL))
            }
            abanKeys.forEach { key ->
                results[key] = if (catalogue.isSuccess) {
                    capture {
                        ProviderParsers.aban(key.providerSymbol, requireNotNull(catalogue.getOrNull()))
                    }
                } else {
                    Result.failure(requireNotNull(catalogue.exceptionOrNull()))
                }
            }
        }

        val otherKeys = canonicalKeys.filterNot { it.provider == PortfolioCodes.ABANTETHER }
        otherKeys.forEach { key ->
            results[key] = capture { fetchOne(key) }
        }
        return results
    }

    private suspend fun fetchOne(key: MarketKey): PriceResult {
        val url = when (key.provider) {
            PortfolioCodes.NOBITEX -> {
                val (base, quote) = ProviderParsers.marketParts(key.providerSymbol)
                "https://apiv2.nobitex.ir/market/stats?srcCurrency=$base&dstCurrency=$quote"
            }
            PortfolioCodes.TSETMC ->
                "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/${key.providerSymbol}"
            PortfolioCodes.RAHAVARD ->
                "https://rahavard365.com/api/v2/asset/${key.providerSymbol}"
            else -> throw PricingException.Unsupported("Unsupported price provider: ${key.provider}")
        }
        val body = loadBody(key.provider, url)
        return when (key.provider) {
            PortfolioCodes.NOBITEX -> ProviderParsers.nobitex(key.providerSymbol, body)
            PortfolioCodes.TSETMC -> ProviderParsers.tsetmc(key.providerSymbol, body)
            PortfolioCodes.RAHAVARD -> ProviderParsers.rahavard(key.providerSymbol, body)
            else -> throw PricingException.Unsupported("Unsupported price provider: ${key.provider}")
        }
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        private const val ABAN_CATALOGUE_URL = "https://api.abantether.com/api/v2/manager/coins"
    }
}
