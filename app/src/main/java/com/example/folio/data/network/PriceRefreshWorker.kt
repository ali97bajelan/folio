package com.example.folio.data.network

import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.AssetValuationEntity
import com.example.folio.data.local.PortfolioDatabase
import com.example.folio.data.local.PortfolioSnapshotEntity
import com.example.folio.data.local.ProviderRefreshEntity
import com.example.folio.domain.HoldingService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Best-effort, public-network enhancement. Failed calls never remove cached prices. */
class PriceRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = Room.databaseBuilder(applicationContext, PortfolioDatabase::class.java, DATABASE_NAME)
            .addMigrations(PortfolioDatabase.MIGRATION_1_2, PortfolioDatabase.MIGRATION_2_3)
            .enableMultiInstanceInvalidation()
            .build()
        try {
            val dao = database.dao()
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val activeAssets = dao.activeAssets()

            PROVIDERS.forEach { provider ->
                refreshProvider(dao, client, provider, activeAssets.filter { asset ->
                    asset.pricingMode == "MARKET" && asset.priceProvider.equals(provider, ignoreCase = true)
                })
            }
            saveBothCurrencyPricesAndValuations(dao, client)
            Result.success()
        } finally {
            database.close()
        }
    }

    private suspend fun refreshProvider(
        dao: com.example.folio.data.local.PortfolioDao,
        client: OkHttpClient,
        provider: String,
        assets: List<AssetEntity>,
    ) {
        val refresh = ProviderRefreshEntity(provider = provider, startedAt = Instant.now(), status = "SUCCESS")
        val refreshId = dao.insertRefresh(refresh)
        val failures = assets.mapNotNull { asset ->
            runCatching {
                val result = fetch(client, asset)
                dao.insertPrice(
                    AssetPriceEntity(
                        assetId = asset.id,
                        price = result.price,
                        currency = result.currency,
                        provider = result.provider,
                        providerSymbol = result.providerSymbol,
                        capturedAt = result.fetchedAt,
                    ),
                )
            }.exceptionOrNull()?.let { "${asset.symbol}: ${it.message ?: "unavailable"}" }
        }
        val status = when {
            failures.isEmpty() -> "SUCCESS"
            failures.size == assets.size -> "FAILED"
            else -> "PARTIAL"
        }
        dao.updateRefresh(
            refresh.copy(
                id = refreshId,
                finishedAt = Instant.now(),
                status = status,
                errorMessage = failures.joinToString(" | "),
            ),
        )
    }

    private fun fetch(client: OkHttpClient, asset: AssetEntity): PriceResult {
        val provider = asset.priceProvider.uppercase()
        val url = when (provider) {
            "NOBITEX" -> ProviderParsers.marketParts(asset.providerSymbol).let { (base, quote) ->
                "https://apiv2.nobitex.ir/market/stats?srcCurrency=$base&dstCurrency=$quote"
            }
            "ABANTETHER" -> "https://api.abantether.com/api/v2/manager/coins"
            "TSETMC" -> "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/${asset.providerSymbol}"
            "RAHAVARD" -> {
                if (asset.providerSymbol.isBlank() || !asset.providerSymbol.all(Char::isDigit)) {
                    throw PricingException.Unsupported("Rahavard requires a numeric entity ID.")
                }
                "https://rahavard365.com/api/v2/asset/${asset.providerSymbol}"
            }
            else -> throw PricingException.Unsupported("Unsupported price provider: $provider")
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", if (provider == "RAHAVARD") "application/json, text/plain, */*" else "application/json")
            .header("User-Agent", userAgentFor(provider))
            .applyProviderHeaders(provider)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PricingException.Unavailable("HTTP ${response.code}")
            val body = response.body?.string()
                ?: throw PricingException.Invalid("The provider returned an empty response.")
            return when (provider) {
                "NOBITEX" -> ProviderParsers.nobitex(asset.providerSymbol, body)
                "ABANTETHER" -> ProviderParsers.aban(asset.providerSymbol, body)
                "TSETMC" -> ProviderParsers.tsetmc(asset.providerSymbol, body)
                "RAHAVARD" -> ProviderParsers.rahavard(asset.providerSymbol, body)
                else -> error("Provider was validated above")
            }
        }
    }

    /**
     * Each refresh stores both representations of every current quote.  A market
     * quoted in USDT/USD is converted with the current USDTIRT quote; an IRT
     * market is divided by that same quote.  The resulting valuation snapshot is
     * therefore complete in both currencies as well.
     */
    private suspend fun saveBothCurrencyPricesAndValuations(
        dao: com.example.folio.data.local.PortfolioDao,
        client: OkHttpClient,
    ) {
        val assets = dao.allAssets()
        if (assets.isEmpty()) return

        // Do not depend on the user having created a separate USDTIRT asset.
        // Nobitex is the canonical source for this conversion used by the app.
        val usdIrt = runCatching { fetch(client, usdtIrtAsset()).price }.getOrNull()
            ?: storedUsdIrt(dao, assets)
            ?: return
        val capturedAt = Instant.now()

        val valuations = dao.activeAssets().mapNotNull { asset ->
            val source = dao.prices(asset.id).firstOrNull() ?: return@mapNotNull null
            val counterpart = convertedPrice(source, usdIrt, capturedAt) ?: return@mapNotNull null
            dao.insertPrice(counterpart.copy(assetId = asset.id))
            val (usdtPrice, tomanPrice) = valuationPrices(source, usdIrt)
            val quantity = HoldingService.quantityAt(dao.transactions(asset.id))
            AssetValuationEntity(assetId = asset.id, quantity = quantity, usdtValue = quantity.multiply(usdtPrice), tomanValue = quantity.multiply(tomanPrice), capturedAt = capturedAt)
        }
        for (valuation in valuations) dao.insertValuation(valuation)
        dao.insertPortfolioSnapshot(PortfolioSnapshotEntity(
            usdtValue = valuations.map { it.usdtValue ?: BigDecimal.ZERO }.fold(BigDecimal.ZERO, BigDecimal::add),
            tomanValue = valuations.map { it.tomanValue ?: BigDecimal.ZERO }.fold(BigDecimal.ZERO, BigDecimal::add),
            capturedAt = capturedAt,
        ))
    }

    private fun usdtIrtAsset() = AssetEntity(
        name = "USDT / IRT",
        symbol = "USDTIRT",
        assetType = "USD",
        priceProvider = "NOBITEX",
        providerSymbol = "USDTIRT",
    )

    private suspend fun storedUsdIrt(
        dao: com.example.folio.data.local.PortfolioDao,
        assets: List<AssetEntity>,
    ): BigDecimal? {
        val aliases = setOf("USD_IRT", "USDTIRT", "USDT_IRT", "USDT")
        val fxAsset = assets.firstOrNull {
            it.symbol.uppercase() in aliases || it.providerSymbol.uppercase() in setOf("USDTIRT", "USDT-RLS")
        } ?: return null
        return dao.prices(fxAsset.id).firstOrNull { it.currency.equals("IRT", ignoreCase = true) }?.price
    }

    private fun convertedPrice(source: AssetPriceEntity, usdIrt: BigDecimal, capturedAt: Instant): AssetPriceEntity? {
        val sourceCurrency = source.currency.uppercase()
        val targetCurrency: String
        val converted: BigDecimal
        when (sourceCurrency) {
            "IRT" -> {
                targetCurrency = "USDT"
                converted = source.price.divide(usdIrt, CONVERSION_SCALE, RoundingMode.HALF_UP)
            }
            "USD", "USDT" -> {
                targetCurrency = "IRT"
                converted = source.price.multiply(usdIrt)
            }
            else -> return null
        }
        return AssetPriceEntity(
            assetId = 0,
            price = converted,
            currency = targetCurrency,
            provider = "CALCULATED",
            providerSymbol = source.providerSymbol,
            capturedAt = capturedAt,
        )
    }

    /** Returns the USDT and toman unit prices from either side of a market pair. */
    private fun valuationPrices(source: AssetPriceEntity, usdIrt: BigDecimal): Pair<BigDecimal, BigDecimal> = when (source.currency.uppercase()) {
        "IRT" -> source.price.divide(usdIrt, CONVERSION_SCALE, RoundingMode.HALF_UP) to source.price
        "USD", "USDT" -> source.price to source.price.multiply(usdIrt)
        else -> error("Unsupported valuation currency: ${source.currency}")
    }

    private fun Request.Builder.applyProviderHeaders(provider: String) = apply {
        when (provider) {
            "NOBITEX" -> {
                header("Referer", "https://nobitex.ir/")
                header("Origin", "https://nobitex.ir")
                header("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
                header("Pragma", "no-cache")
            }
            "RAHAVARD" -> {
                header("Referer", "https://rahavard365.com/")
                header("Application-Name", "rahavard")
                header("Platform", "web")
            }
        }
    }

    private fun userAgentFor(provider: String): String = when (provider) {
        "NOBITEX" -> "Mozilla/5.0 (Android; Folio)"
        "RAHAVARD" -> "Mozilla/5.0 (compatible; AssetsDashboard/1.0)"
        else -> "Folio Android"
    }

    companion object {
        private const val DATABASE_NAME = "folio.db"
        private const val PERIODIC_WORK_NAME = "folio-price-refresh"
        private const val IMMEDIATE_WORK_NAME = "folio-price-refresh-now"
        private val PROVIDERS = listOf("NOBITEX", "ABANTETHER", "TSETMC", "RAHAVARD")
        private const val CONVERSION_SCALE = 20

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Enqueues an immediate refresh and returns its id so callers can await its completion. */
        fun refreshNow(context: Context): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            return request.id
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
