package com.example.folio.data.widget

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "widget_configuration")
data class WidgetConfigurationEntity(
    @PrimaryKey @ColumnInfo(name = "app_widget_id") val appWidgetId: Int,
    val kind: String,
    val currency: String,
    @ColumnInfo(name = "hide_total") val hideTotal: Boolean,
    @ColumnInfo(name = "market_provider") val marketProvider: String?,
    @ColumnInfo(name = "market_provider_symbol") val marketProviderSymbol: String?,
    @ColumnInfo(name = "market_display_name") val marketDisplayName: String?,
    @ColumnInfo(name = "market_symbol") val marketSymbol: String?,
    @ColumnInfo(name = "market_asset_type") val marketAssetType: String?,
    val revision: Long,
)

@Entity(
    tableName = "widget_quote",
    primaryKeys = ["provider", "provider_symbol"],
)
data class WidgetQuoteEntity(
    val provider: String,
    @ColumnInfo(name = "provider_symbol") val providerSymbol: String,
    val price: BigDecimal?,
    val currency: String?,
    @ColumnInfo(name = "quote_at") val quoteAt: Instant?,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Instant?,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Instant?,
    val status: String,
    @ColumnInfo(name = "last_error") val lastError: String?,
)

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widget_configuration WHERE app_widget_id = :appWidgetId")
    suspend fun configuration(appWidgetId: Int): WidgetConfigurationEntity?

    @Query("SELECT * FROM widget_configuration ORDER BY app_widget_id")
    suspend fun configurations(): List<WidgetConfigurationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putConfiguration(configuration: WidgetConfigurationEntity)

    @Query("DELETE FROM widget_configuration WHERE app_widget_id IN (:appWidgetIds)")
    suspend fun deleteConfigurations(appWidgetIds: Set<Int>)

    @Query("UPDATE widget_configuration SET app_widget_id = :newId WHERE app_widget_id = :oldId")
    suspend fun updateConfigurationId(oldId: Int, newId: Int): Int

    @Query(
        """
        SELECT * FROM widget_quote
        WHERE provider = :provider AND provider_symbol = :providerSymbol
        """,
    )
    suspend fun quote(provider: String, providerSymbol: String): WidgetQuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putQuote(quote: WidgetQuoteEntity)

    @Query(
        """
        DELETE FROM widget_quote
        WHERE NOT EXISTS (
            SELECT 1 FROM widget_configuration
            WHERE widget_configuration.market_provider = widget_quote.provider
              AND widget_configuration.market_provider_symbol = widget_quote.provider_symbol
        )
        """,
    )
    suspend fun deleteUnreferencedQuotes()

    @Transaction
    suspend fun saveConfiguration(candidate: WidgetConfigurationEntity): WidgetConfigurationEntity {
        val current = configuration(candidate.appWidgetId)
        val saved = candidate.copy(revision = Math.addExact(current?.revision ?: 0L, 1L))
        putConfiguration(saved)
        return saved
    }

    @Transaction
    suspend fun setTotalHidden(appWidgetId: Int, hidden: Boolean): WidgetConfigurationEntity {
        val current = configuration(appWidgetId)
            ?: throw IllegalStateException("No widget configuration exists for id $appWidgetId.")
        check(current.kind == WidgetKind.PORTFOLIO.name) {
            "Only portfolio widgets have a hide-total setting."
        }
        if (current.hideTotal == hidden) return current

        val updated = current.copy(
            hideTotal = hidden,
            revision = Math.addExact(current.revision, 1L),
        )
        putConfiguration(updated)
        return updated
    }

    @Transaction
    suspend fun removeConfigurationsAndPrune(appWidgetIds: Set<Int>) {
        if (appWidgetIds.isEmpty()) return
        deleteConfigurations(appWidgetIds)
        deleteUnreferencedQuotes()
    }

    @Transaction
    suspend fun storeQuote(incoming: WidgetQuoteEntity) {
        val previous = quote(incoming.provider, incoming.providerSymbol)?.toModel()
        val updated = mergeWidgetQuote(previous, incoming.toModel()) ?: return
        putQuote(updated.toEntity())
    }

    @Transaction
    suspend fun remapConfigurationIds(oldToNew: Map<Int, Int>) {
        if (oldToNew.isEmpty()) return
        require(oldToNew.keys.all { it > 0 } && oldToNew.values.all { it > 0 }) {
            "Widget ids must be positive."
        }

        val existingIds = configurations().mapTo(mutableSetOf()) { it.appWidgetId }
        val mappings = oldToNew.filter { (oldId, newId) ->
            oldId in existingIds && oldId != newId
        }
        if (mappings.isEmpty()) return

        require(mappings.values.size == mappings.values.toSet().size) {
            "Restored widget ids must be unique."
        }
        val destinationsOccupiedByUnmappedRows = mappings.values.toSet() intersect (existingIds - mappings.keys)
        require(destinationsOccupiedByUnmappedRows.isEmpty()) {
            "A restored widget id is already assigned to an unrelated configuration."
        }

        val reservedIds = existingIds + mappings.values
        var nextTemporaryId = -1
        val temporaryIds = mappings.keys.associateWith {
            while (nextTemporaryId in reservedIds) nextTemporaryId--
            check(nextTemporaryId < 0) { "Unable to allocate a temporary widget id." }
            nextTemporaryId--
        }

        for ((oldId, temporaryId) in temporaryIds) {
            check(updateConfigurationId(oldId, temporaryId) == 1) {
                "Widget configuration disappeared during ID remapping."
            }
        }
        for ((oldId, newId) in mappings) {
            val temporaryId = requireNotNull(temporaryIds[oldId])
            check(updateConfigurationId(temporaryId, newId) == 1) {
                "Widget configuration disappeared during ID remapping."
            }
        }
    }
}

internal fun WidgetConfigurationEntity.toModel(): WidgetConfiguration {
    val market = marketProvider?.let { provider ->
        MarketSelection(
            key = MarketKey(
                provider = provider,
                providerSymbol = requireNotNull(marketProviderSymbol),
            ),
            displayName = requireNotNull(marketDisplayName),
            symbol = requireNotNull(marketSymbol),
            assetType = requireNotNull(marketAssetType),
        )
    }
    return WidgetConfiguration(
        appWidgetId = appWidgetId,
        kind = WidgetKind.valueOf(kind),
        currency = currency,
        hideTotal = hideTotal,
        market = market,
        revision = revision,
    )
}

internal fun WidgetConfiguration.toEntity(): WidgetConfigurationEntity =
    WidgetConfigurationEntity(
        appWidgetId = appWidgetId,
        kind = kind.name,
        currency = currency,
        hideTotal = hideTotal,
        marketProvider = market?.key?.provider,
        marketProviderSymbol = market?.key?.providerSymbol,
        marketDisplayName = market?.displayName,
        marketSymbol = market?.symbol,
        marketAssetType = market?.assetType,
        revision = revision,
    )

internal fun WidgetQuoteEntity.toModel(): WidgetQuote =
    WidgetQuote(
        key = MarketKey(provider, providerSymbol),
        price = price,
        currency = currency,
        quoteAt = quoteAt,
        lastSuccessAt = lastSuccessAt,
        lastAttemptAt = lastAttemptAt,
        status = QuoteStatus.valueOf(status),
        lastError = lastError,
    )

internal fun WidgetQuote.toEntity(): WidgetQuoteEntity =
    WidgetQuoteEntity(
        provider = key.provider,
        providerSymbol = key.providerSymbol,
        price = price,
        currency = currency,
        quoteAt = quoteAt,
        lastSuccessAt = lastSuccessAt,
        lastAttemptAt = lastAttemptAt,
        status = status.name,
        lastError = lastError,
    )
