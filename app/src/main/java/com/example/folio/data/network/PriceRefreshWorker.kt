package com.example.folio.data.network

import com.example.folio.PortfolioCodes
import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.folio.data.local.AssetEntity
import com.example.folio.data.local.AssetPriceEntity
import com.example.folio.data.local.AssetValuationEntity
import com.example.folio.data.local.PortfolioDao
import com.example.folio.FolioApplication
import com.example.folio.data.local.PortfolioSnapshotEntity
import com.example.folio.data.local.ProviderRefreshEntity
import com.example.folio.domain.HoldingService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Best-effort, public-network enhancement. Failed calls never remove cached prices. */
class PriceRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = (applicationContext as FolioApplication).database.dao()
        val activeAssets = dao.activeAssets()

        PROVIDERS.forEach { provider ->
            refreshProvider(dao, client, provider, activeAssets.filter { asset ->
                asset.pricingMode == PortfolioCodes.MARKET && asset.priceProvider.equals(provider, ignoreCase = true)
            })
        }
        saveBothCurrencyPricesAndValuations(dao, client)
        Result.success()
    }

    private suspend fun refreshProvider(
        dao: PortfolioDao,
        client: OkHttpClient,
        provider: String,
        assets: List<AssetEntity>,
    ) {
        val refresh = ProviderRefreshEntity(provider = provider, startedAt = Instant.now(), status = PortfolioCodes.SUCCESS)
        val refreshId = dao.insertRefresh(refresh)
        // Download once, including when the request fails, and skip empty providers.
        val abanCatalogue = if (provider == PortfolioCodes.ABANTETHER && assets.isNotEmpty()) {
            runCatching { ProviderParsers.abanCatalogue(requestBody(client, provider, ABAN_CATALOGUE_URL)) }
                .onFailure { if (it is CancellationException) throw it }
        } else null
        val failures = assets.mapNotNull { asset ->
            runCatching {
                val result = if (provider == PortfolioCodes.ABANTETHER) {
                    ProviderParsers.aban(asset.providerSymbol, requireNotNull(abanCatalogue).getOrThrow())
                } else {
                    fetch(client, asset)
                }
                dao.insertPriceForConfiguration(
                    asset,
                    AssetPriceEntity(
                        assetId = asset.id,
                        price = result.price,
                        currency = result.currency,
                        provider = result.provider,
                        providerSymbol = result.providerSymbol,
                        capturedAt = result.fetchedAt,
                    ),
                )
            }.onFailure { if (it is CancellationException) throw it }
                .exceptionOrNull()?.let { "${asset.symbol}: ${it.message ?: "unavailable"}" }
        }
        val status = when {
            failures.isEmpty() -> PortfolioCodes.SUCCESS
            failures.size == assets.size -> PortfolioCodes.FAILED
            else -> PortfolioCodes.PARTIAL
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

    private suspend fun fetch(client: OkHttpClient, asset: AssetEntity): PriceResult {
        val provider = asset.priceProvider.uppercase()
        val url = when (provider) {
            PortfolioCodes.NOBITEX -> ProviderParsers.marketParts(asset.providerSymbol).let { (base, quote) ->
                "https://apiv2.nobitex.ir/market/stats?srcCurrency=$base&dstCurrency=$quote"
            }
            PortfolioCodes.ABANTETHER -> ABAN_CATALOGUE_URL
            PortfolioCodes.TSETMC -> "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/${asset.providerSymbol}"
            PortfolioCodes.RAHAVARD -> {
                if (asset.providerSymbol.isBlank() || !asset.providerSymbol.all(Char::isDigit)) {
                    throw PricingException.Unsupported("Rahavard requires a numeric entity ID.")
                }
                "https://rahavard365.com/api/v2/asset/${asset.providerSymbol}"
            }
            else -> throw PricingException.Unsupported("Unsupported price provider: $provider")
        }
        val body = requestBody(client, provider, url)
        return when (provider) {
            PortfolioCodes.NOBITEX -> ProviderParsers.nobitex(asset.providerSymbol, body)
            PortfolioCodes.ABANTETHER -> ProviderParsers.aban(asset.providerSymbol, body)
            PortfolioCodes.TSETMC -> ProviderParsers.tsetmc(asset.providerSymbol, body)
            PortfolioCodes.RAHAVARD -> ProviderParsers.rahavard(asset.providerSymbol, body)
            else -> error("Provider was validated above")
        }
    }

    private suspend fun requestBody(client: OkHttpClient, provider: String, url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", if (provider == PortfolioCodes.RAHAVARD) "application/json, text/plain, */*" else "application/json")
            .header("User-Agent", userAgentFor(provider))
            .applyProviderHeaders(provider)
            .build()

        return client.newCall(request).awaitBody()
    }

    /**
     * Each refresh stores both representations of every current quote.  A market
     * quoted in USDT/USD is converted with the current USDTIRT quote; an IRT
     * market is divided by that same quote.  The resulting valuation snapshot is
     * therefore complete in both currencies as well.
     */
    private suspend fun saveBothCurrencyPricesAndValuations(
        dao: PortfolioDao,
        client: OkHttpClient,
    ) {
        val assets = dao.allAssets()
        if (assets.isEmpty()) return

        // Do not depend on the user having created a separate USDTIRT asset.
        // Nobitex is the canonical source for this conversion used by the app.
        val fetchedUsdIrt = runCatching { fetch(client, usdtIrtAsset()).price }
            .onFailure { if (it is CancellationException) throw it }.getOrNull()
        val database = (applicationContext as FolioApplication).database
        database.withTransaction {
            val usdIrt = fetchedUsdIrt ?: storedUsdIrt(dao, dao.allAssets()) ?: return@withTransaction
            val capturedAt = Instant.now()

            val valuations = dao.activeAssets().mapNotNull { asset ->
                saveCounterpartAndValueAsset(dao, asset, usdIrt, capturedAt)
            }
            dao.insertValuationSnapshot(valuations, PortfolioSnapshotEntity(
                usdtValue = valuations.map { it.usdtValue ?: BigDecimal.ZERO }.fold(BigDecimal.ZERO, BigDecimal::add),
                tomanValue = valuations.map { it.tomanValue ?: BigDecimal.ZERO }.fold(BigDecimal.ZERO, BigDecimal::add),
                capturedAt = capturedAt,
            ))
        }
    }

    private suspend fun saveCounterpartAndValueAsset(
        dao: PortfolioDao,
        asset: AssetEntity,
        usdIrt: BigDecimal,
        capturedAt: Instant,
    ): AssetValuationEntity? {
        val source = dao.prices(asset.id).firstOrNull { it.isSourcePriceFor(asset) } ?: return null
        val counterpart = convertedPrice(source, usdIrt, capturedAt) ?: return null
        dao.insertPriceForConfiguration(asset, counterpart.copy(assetId = asset.id)) ?: return null
        val (usdtPrice, tomanPrice) = valuationPrices(source, usdIrt)
        val quantity = HoldingService.quantityAt(dao.transactions(asset.id))
        return AssetValuationEntity(
            assetId = asset.id,
            quantity = quantity,
            usdtValue = quantity.multiply(usdtPrice),
            tomanValue = quantity.multiply(tomanPrice),
            capturedAt = capturedAt,
        )
    }

    // Derived quotes must never become the source of a later FX conversion.
    private fun AssetPriceEntity.isSourcePriceFor(asset: AssetEntity): Boolean =
        if (asset.pricingMode == PortfolioCodes.MANUAL) {
            provider.equals(PortfolioCodes.MANUAL, ignoreCase = true)
        } else {
            provider.equals(asset.priceProvider, ignoreCase = true) &&
                providerSymbol.equals(asset.providerSymbol, ignoreCase = true) &&
                !provider.equals(PortfolioCodes.CALCULATED, ignoreCase = true)
        }

    private fun usdtIrtAsset() = AssetEntity(
        name = "USDT / IRT",
        symbol = "USDTIRT",
        assetType = PortfolioCodes.USD,
        priceProvider = PortfolioCodes.NOBITEX,
        providerSymbol = "USDTIRT",
    )

    private suspend fun storedUsdIrt(
        dao: PortfolioDao,
        assets: List<AssetEntity>,
    ): BigDecimal? {
        val aliases = setOf("USD_IRT", "USDTIRT", "USDT_IRT", PortfolioCodes.USDT)
        val fxAsset = assets.firstOrNull {
            it.symbol.uppercase() in aliases || it.providerSymbol.uppercase() in setOf("USDTIRT", "USDT-RLS")
        } ?: return null
        return dao.prices(fxAsset.id).firstOrNull {
            it.currency.equals(PortfolioCodes.IRT, ignoreCase = true) &&
                !it.provider.equals(PortfolioCodes.CALCULATED, ignoreCase = true)
        }?.price
    }

    private fun convertedPrice(source: AssetPriceEntity, usdIrt: BigDecimal, capturedAt: Instant): AssetPriceEntity? {
        val sourceCurrency = source.currency.uppercase()
        val targetCurrency: String
        val converted: BigDecimal
        when (sourceCurrency) {
            PortfolioCodes.IRT -> {
                targetCurrency = PortfolioCodes.USDT
                converted = source.price.divide(usdIrt, CONVERSION_SCALE, RoundingMode.HALF_UP)
            }
            PortfolioCodes.USD, PortfolioCodes.USDT -> {
                targetCurrency = PortfolioCodes.IRT
                converted = source.price.multiply(usdIrt)
            }
            else -> return null
        }
        return AssetPriceEntity(
            assetId = 0,
            price = converted,
            currency = targetCurrency,
            provider = PortfolioCodes.CALCULATED,
            providerSymbol = source.providerSymbol,
            capturedAt = capturedAt,
        )
    }

    /** Returns the USDT and toman unit prices from either side of a market pair. */
    private fun valuationPrices(source: AssetPriceEntity, usdIrt: BigDecimal): Pair<BigDecimal, BigDecimal> = when (source.currency.uppercase()) {
        PortfolioCodes.IRT -> source.price.divide(usdIrt, CONVERSION_SCALE, RoundingMode.HALF_UP) to source.price
        PortfolioCodes.USD, PortfolioCodes.USDT -> source.price to source.price.multiply(usdIrt)
        else -> error("Unsupported valuation currency: ${source.currency}")
    }

    private fun Request.Builder.applyProviderHeaders(provider: String) = apply {
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

    private fun userAgentFor(provider: String): String = when (provider) {
        PortfolioCodes.NOBITEX -> "Mozilla/5.0 (Android; Folio)"
        PortfolioCodes.RAHAVARD -> "Mozilla/5.0 (compatible; AssetsDashboard/1.0)"
        else -> "Folio Android"
    }

    companion object {
        private const val ABAN_CATALOGUE_URL = "https://api.abantether.com/api/v2/manager/coins"
        private val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        private const val PERIODIC_WORK_NAME = "folio-price-refresh"
        internal const val IMMEDIATE_WORK_NAME = "folio-price-refresh-now"
        private val PROVIDERS = listOf(PortfolioCodes.NOBITEX, PortfolioCodes.ABANTETHER, PortfolioCodes.TSETMC, PortfolioCodes.RAHAVARD)
        private const val CONVERSION_SCALE = 20

        /** Refreshes once when the app opens and removes the previous periodic schedule. */
        fun refreshOnAppOpen(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
            refreshNow(context)
        }

        /** Callers can await enqueueing, then observe the actual work by its unique name. */
        fun refreshNow(context: Context): Operation {
            val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
                .setConstraints(networkConstraints())
                .build()
            return WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
