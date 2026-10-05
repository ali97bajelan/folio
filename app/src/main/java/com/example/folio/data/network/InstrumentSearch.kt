package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit

data class Instrument(
    val providerSymbol: String,
    val assetSymbol: String,
    val symbol: String,
    val name: String,
    val meta: String,
)

/** Public provider catalogues used while creating a market-priced asset. */
object InstrumentSearch {
    private const val MAX_RESULTS = 12
    private const val USER_AGENT = "Mozilla/5.0 (Android; Folio)"
    private const val NOBITEX_CACHE_CONTROL = "no-store, no-cache, must-revalidate, proxy-revalidate"

    /** Common Persian and English names accepted by Nobitex's symbol-only API. */
    private val nobitexAliases = mapOf(
        "bitcoin" to "btc", "بیتکوین" to "btc", "اتریوم" to "eth", "tether" to "usdt", "تتر" to "usdt",
        "binancecoin" to "bnb", "بایننسکوین" to "bnb", "ripple" to "xrp", "ریپل" to "xrp",
        "usdcoin" to "usdc", "سولانا" to "sol", "tron" to "trx", "ترون" to "trx", "zcash" to "zec",
        "dogecoin" to "doge", "دوجکوین" to "doge", "monero" to "xmr", "مونرو" to "xmr",
        "chainlink" to "link", "چینلینک" to "link", "cardano" to "ada", "کاردانو" to "ada",
        "stellar" to "xlm", "استلار" to "xlm", "uniswap" to "uni", "یونیسواپ" to "uni",
        "bitcoincash" to "bch", "بیتکوینکش" to "bch", "litecoin" to "ltc", "لایتکوین" to "ltc",
        "avalanche" to "avax", "آوالانچ" to "avax", "hedera" to "hbar", "هدرا" to "hbar",
        "shiba" to "shib", "شیبا" to "shib", "polkadot" to "dot", "پولکادات" to "dot",
        "paxgold" to "paxg", "پکسگلد" to "paxg", "internetcomputer" to "icp", "ورلدکوین" to "wld",
        "arbitrum" to "arb", "آربیتروم" to "arb", "ethereumclassic" to "etc", "کسپا" to "kas",
        "cosmos" to "atom", "کازماس" to "atom", "algorand" to "algo", "الگوراند" to "algo",
        "filecoin" to "fil", "فایلکوین" to "fil", "dash" to "dash", "دش" to "dash",
        "injective" to "inj", "اینجکتیو" to "inj", "vechain" to "vet", "ویچین" to "vet",
        "aptos" to "apt", "آپتوس" to "apt", "flr" to "flr", "فلر" to "flr",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        provider: String,
        query: String,
        assetType: String,
    ): List<Instrument> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isBlank()) return@withContext emptyList()

        when (provider.uppercase()) {
            PortfolioCodes.NOBITEX -> searchNobitex(term)
            PortfolioCodes.ABANTETHER -> searchAbanTether(term)
            PortfolioCodes.TSETMC -> searchTsetmc(term)
            PortfolioCodes.RAHAVARD -> searchRahavard(term, assetType)
            else -> emptyList()
        }
    }

    private suspend fun requestBody(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .applyProviderHeaders(url)
            .build()

        return client.newCall(request).awaitBody()
    }

    private fun Request.Builder.applyProviderHeaders(url: String): Request.Builder = apply {
        when {
            url.contains("nobitex") -> {
                header("Referer", "https://nobitex.ir/")
                header("Origin", "https://nobitex.ir")
                header("Cache-Control", NOBITEX_CACHE_CONTROL)
                header("Pragma", "no-cache")
            }

            url.contains("abantether") -> {
                header("Referer", "https://abantether.com/")
                header("Origin", "https://abantether.com")
            }

            url.contains("tsetmc") -> header("Referer", "https://www.tsetmc.com/")

            url.contains("rahavard") -> {
                header("Accept", "application/json, text/plain, */*")
                header("User-Agent", "Mozilla/5.0 (compatible; AssetsDashboard/1.0)")
                header("Referer", "https://rahavard365.com/")
                header("Application-Name", "rahavard")
                header("Platform", "web")
            }
        }
    }

    private suspend fun searchNobitex(query: String): List<Instrument> {
        val sourceCurrency = nobitexSourceCurrency(query) ?: return emptyList()
        val url = "https://apiv2.nobitex.ir/market/stats?srcCurrency=$sourceCurrency"
        val response = JSONObject(requestBody(url))
        if (!response.optString("status").equals("ok", ignoreCase = true)) {
            throw PricingException.Invalid("Nobitex did not return an OK response.")
        }
        val statistics = response.optJSONObject("stats") ?: return emptyList()

        return statistics.keys().asSequence()
            .mapNotNull { market -> nobitexInstrument(market, sourceCurrency) }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun nobitexSourceCurrency(query: String): String? {
        // A saved/typed pair (BTCUSDT, USDTIRT, BTC-RLS) is already unambiguous.
        ProviderParsers.marketPartsOrNull(query)?.let { return it.first }

        val normalizedQuery = normalizeNobitexQuery(query)
        if (normalizedQuery.isBlank()) return null

        nobitexAliases[normalizedQuery]?.let { return it }
        return nobitexAliases.entries.firstOrNull { (name, symbol) ->
            name.contains(normalizedQuery) || symbol.contains(normalizedQuery)
        }?.value ?: normalizedQuery.takeIf { it.all(Char::isLetterOrDigit) }
    }

    private fun normalizeNobitexQuery(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .filter(Char::isLetterOrDigit)

    private fun ProviderParsers.marketPartsOrNull(symbol: String): Pair<String, String>? =
        runCatching { marketParts(symbol) }.getOrNull()

    private fun nobitexInstrument(market: String, sourceCurrency: String): Instrument? {
        val parts = market.split('-', limit = 2)
        if (parts.size != 2 || parts[0] != sourceCurrency) return null

        val base = parts[0]
        val quote = if (parts[1] == "rls") PortfolioCodes.IRT else parts[1].uppercase()
        val displayBase = base.uppercase()
        return Instrument(
            providerSymbol = "$base$quote".uppercase(),
            assetSymbol = displayBase,
            symbol = "$displayBase/$quote",
            name = "Nobitex $displayBase / $quote",
            meta = "Nobitex market",
        )
    }

    private suspend fun searchAbanTether(query: String): List<Instrument> {
        val needle = query.lowercase()
        val coins = JSONObject(requestBody("https://api.abantether.com/api/v2/manager/coins"))
            .optJSONArray("data")
            ?: return emptyList()

        return coins.objects()
            .filter { it.optBoolean("is_active") }
            .mapNotNull { coin -> abanTetherInstrument(coin, needle) }
            .take(MAX_RESULTS)
    }

    private fun abanTetherInstrument(coin: JSONObject, needle: String): Instrument? {
        val symbol = coin.optString("symbol").uppercase()
        val englishName = coin.optString("name")
        val name = coin.optString("persian_name").ifBlank { englishName }
        val isMatch = listOf(symbol, name, englishName).any { it.lowercase().contains(needle) }
        if (!isMatch) return null

        return Instrument(
            providerSymbol = symbol,
            assetSymbol = symbol,
            symbol = symbol,
            name = name.ifBlank { symbol },
            meta = coin.optString("name", "Aban Tether asset"),
        )
    }

    private suspend fun searchTsetmc(query: String): List<Instrument> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/$encodedQuery"
        val results = JSONObject(requestBody(url)).optJSONArray("instrumentSearch") ?: return emptyList()

        return results.objects()
            .mapNotNull { item ->
                val id = item.optString("insCode")
                if (id.isBlank()) return@mapNotNull null

                val ticker = item.optString("lVal18AFC", id)
                Instrument(
                    providerSymbol = id,
                    assetSymbol = ticker,
                    symbol = ticker,
                    name = item.optString("lVal30"),
                    meta = "InsCode: $id",
                )
            }
            .take(MAX_RESULTS)
    }

    private suspend fun searchRahavard(query: String, assetType: String): List<Instrument> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val results = JSONObject(requestBody("https://rahavard365.com/api/v2/search?keyword=$encodedQuery"))
            .optJSONArray("data")
            ?: throw PricingException.Invalid("Rahavard returned an unexpected search response.")

        val queryWords = query.wordsForRahavard()
        val searchesForPreciousMetal = queryWords.any { word ->
            listOf("طلا", "نقره", "gold", "silver").any(word::contains)
        }
        val preferredType = mapOf(
            PortfolioCodes.GOLD to "کالا",
            PortfolioCodes.SILVER to "کالا",
            PortfolioCodes.CRYPTO to "رمز ارز",
            PortfolioCodes.IRAN_STOCK to "سهام",
            PortfolioCodes.US_STOCK to "سهام",
        )[assetType]

        return results.objects()
            .filter { it.isValidRahavardResult(queryWords, searchesForPreciousMetal) }
            .sortedBy { it.optString("type") != preferredType }
            .map(::rahavardInstrument)
            .take(MAX_RESULTS)
    }

    private fun JSONObject.isValidRahavardResult(
        queryWords: List<String>,
        searchesForPreciousMetal: Boolean,
    ): Boolean {
        val name = optString("name")
        val nameWords = name.wordsForRahavard()
        val matchesEveryWord = queryWords.all { queryWord ->
            nameWords.any { it.startsWith(queryWord) }
        }
        val isCommodityListing = optString("exchange").isEmpty()

        return optString("entity_id").isNotBlank() &&
            !optBoolean("unlisted_item") &&
            name.isNotBlank() &&
            matchesEveryWord &&
            (!searchesForPreciousMetal || isCommodityListing)
    }

    private fun rahavardInstrument(item: JSONObject): Instrument {
        val id = item.optString("entity_id")
        val ticker = item.optString("trade_symbol")
            .ifBlank { item.optString("short_name") }
            .ifBlank { item.optString("name") }

        return Instrument(
            providerSymbol = id,
            assetSymbol = ticker,
            symbol = ticker,
            name = item.optString("short_name").ifBlank { item.optString("name") },
            meta = listOf(
                item.optString("exchange"),
                item.optString("type"),
                "Rahavard ID: $id",
            ).filter(String::isNotBlank).joinToString(" · "),
        )
    }

    private fun String.wordsForRahavard(): List<String> = split(Regex("\\s+"))
        .map(::normalizeRahavardText)
        .filter(String::isNotBlank)

    private fun normalizeRahavardText(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .filterNot(Char::isWhitespace)

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}
