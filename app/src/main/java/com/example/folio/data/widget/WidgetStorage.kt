package com.example.folio.data.widget

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "widget_configuration")
data class WidgetConfigurationEntity(
    @PrimaryKey @ColumnInfo(name = "app_widget_id") val appWidgetId: Int,
    val kind: String,
    val currency: String,
    @ColumnInfo(name = "hide_total") val hideTotal: Boolean,
    val revision: Long,
)

@Entity(
    tableName = "widget_market_selection",
    foreignKeys = [
        ForeignKey(
            entity = WidgetConfigurationEntity::class,
            parentColumns = ["app_widget_id"],
            childColumns = ["app_widget_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["app_widget_id"]),
        Index(value = ["provider", "provider_symbol"]),
        Index(value = ["app_widget_id", "provider", "provider_symbol"], unique = true),
    ],
)
data class WidgetMarketSelectionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "selection_id") val selectionId: Long = 0,
    @ColumnInfo(name = "app_widget_id") val appWidgetId: Int,
    val position: Int,
    val provider: String,
    @ColumnInfo(name = "provider_symbol") val providerSymbol: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val symbol: String,
    @ColumnInfo(name = "asset_type") val assetType: String,
)

data class WidgetConfigurationWithSelections(
    @Embedded val configuration: WidgetConfigurationEntity,
    @Relation(
        parentColumn = "app_widget_id",
        entityColumn = "app_widget_id",
    )
    val selections: List<WidgetMarketSelectionEntity>,
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
    @Transaction
    @Query("SELECT * FROM widget_configuration WHERE app_widget_id = :appWidgetId")
    suspend fun configuration(appWidgetId: Int): WidgetConfigurationWithSelections?

    @Transaction
    @Query("SELECT * FROM widget_configuration ORDER BY app_widget_id")
    suspend fun configurations(): List<WidgetConfigurationWithSelections>

    @Query("SELECT * FROM widget_market_selection WHERE app_widget_id = :appWidgetId ORDER BY position")
    suspend fun selections(appWidgetId: Int): List<WidgetMarketSelectionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConfiguration(configuration: WidgetConfigurationEntity)

    @Update
    suspend fun updateConfiguration(configuration: WidgetConfigurationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSelections(selections: List<WidgetMarketSelectionEntity>)

    @Query("DELETE FROM widget_market_selection WHERE app_widget_id = :appWidgetId")
    suspend fun deleteSelections(appWidgetId: Int)

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
            SELECT 1 FROM widget_market_selection
            WHERE widget_market_selection.provider = widget_quote.provider
              AND widget_market_selection.provider_symbol = widget_quote.provider_symbol
        )
        """,
    )
    suspend fun deleteUnreferencedQuotes()

    @Transaction
    suspend fun saveConfiguration(candidate: WidgetConfiguration): WidgetConfigurationWithSelections {
        val current = configuration(candidate.appWidgetId)?.configuration
        val saved = WidgetConfigurationEntity(
            appWidgetId = candidate.appWidgetId,
            kind = candidate.kind.name,
            currency = candidate.currency,
            hideTotal = candidate.hideTotal,
            revision = Math.addExact(current?.revision ?: 0L, 1L),
        )
        if (current == null) insertConfiguration(saved) else updateConfiguration(saved)

        val oldByKey = selections(candidate.appWidgetId).associateBy {
            MarketKey(it.provider, it.providerSymbol)
        }
        val newSelections = candidate.markets.mapIndexed { position, market ->
            WidgetMarketSelectionEntity(
                selectionId = oldByKey[market.key]?.selectionId ?: 0L,
                appWidgetId = candidate.appWidgetId,
                position = position,
                provider = market.key.provider,
                providerSymbol = market.key.providerSymbol,
                displayName = market.displayName,
                symbol = market.symbol,
                assetType = market.assetType,
            )
        }
        deleteSelections(candidate.appWidgetId)
        if (newSelections.isNotEmpty()) insertSelections(newSelections)
        deleteUnreferencedQuotes()
        return requireNotNull(configuration(candidate.appWidgetId))
    }

    @Transaction
    suspend fun setTotalHidden(appWidgetId: Int, hidden: Boolean): WidgetConfigurationWithSelections {
        val current = configuration(appWidgetId)
            ?: throw IllegalStateException("No widget configuration exists for id $appWidgetId.")
        check(current.configuration.kind == WidgetKind.PORTFOLIO.name) {
            "Only portfolio widgets have a hide-total setting."
        }
        if (current.configuration.hideTotal == hidden) return current

        updateConfiguration(
            current.configuration.copy(
                hideTotal = hidden,
                revision = Math.addExact(current.configuration.revision, 1L),
            ),
        )
        return requireNotNull(configuration(appWidgetId))
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
    suspend fun storeQuoteIfConfigurationCurrent(
        appWidgetId: Int,
        expectedRevision: Long,
        savedKey: MarketKey,
        incoming: WidgetQuoteEntity,
    ): Boolean {
        val current = configuration(appWidgetId) ?: return false
        val stillSelected = current.selections.any {
            it.provider == savedKey.provider && it.providerSymbol == savedKey.providerSymbol
        }
        if (
            current.configuration.kind != WidgetKind.ASSET_PRICE.name ||
            current.configuration.revision != expectedRevision ||
            !stillSelected
        ) {
            return false
        }
        storeQuote(incoming)
        return true
    }

    @Transaction
    suspend fun remapConfigurationIds(oldToNew: Map<Int, Int>) {
        if (oldToNew.isEmpty()) return
        require(oldToNew.keys.all { it > 0 } && oldToNew.values.all { it > 0 }) {
            "Widget ids must be positive."
        }

        val existingIds = configurations().mapTo(mutableSetOf()) { it.configuration.appWidgetId }
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

internal fun WidgetConfigurationWithSelections.toModel(): WidgetConfiguration =
    WidgetConfiguration(
        appWidgetId = configuration.appWidgetId,
        kind = WidgetKind.valueOf(configuration.kind),
        currency = configuration.currency,
        hideTotal = configuration.hideTotal,
        markets = selections.sortedBy { it.position }.map(WidgetMarketSelectionEntity::toModel),
        revision = configuration.revision,
    )

internal fun WidgetMarketSelectionEntity.toModel(): MarketSelection =
    MarketSelection(
        key = MarketKey(provider, providerSymbol),
        displayName = displayName,
        symbol = symbol,
        assetType = assetType,
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
