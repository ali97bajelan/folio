package com.example.folio.data.local

import com.example.folio.PortfolioCodes
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.folio.data.widget.WidgetConfigurationEntity
import com.example.folio.data.widget.WidgetMarketSelectionEntity
import com.example.folio.data.widget.WidgetDao
import com.example.folio.data.widget.WidgetQuoteEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

class DbConverters {
    @TypeConverter fun decimal(value: BigDecimal?): String? = value?.toPlainString()
    @TypeConverter fun decimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
    @TypeConverter fun instant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun instant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

@Entity(tableName = "portfolio_tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(), @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now())
@Entity(tableName = "portfolio_location", indices = [Index(value = ["name"], unique = true)])
data class LocationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val notes: String = "", @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(), @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now())
@Entity(tableName = "portfolio_asset", indices = [Index(value = ["symbol"], unique = true)])
data class AssetEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val symbol: String, @ColumnInfo(name = "asset_type") val assetType: String, val unit: String = PortfolioCodes.UNIT, @ColumnInfo(name = "pricing_mode") val pricingMode: String = PortfolioCodes.MARKET, @ColumnInfo(name = "price_provider") val priceProvider: String = "", @ColumnInfo(name = "provider_symbol") val providerSymbol: String = "", @ColumnInfo(name = "manual_price_currency") val manualPriceCurrency: String = PortfolioCodes.IRT, @ColumnInfo(name = "is_active") val isActive: Boolean = true, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(), @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now())
@Entity(tableName = "portfolio_asset_tags", indices = [Index(value = ["asset_id", "tag_id"], unique = true), Index(value = ["asset_id"]), Index(value = ["tag_id"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE)])
data class AssetTagCrossRef(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, @ColumnInfo(name = "tag_id") val tagId: Long)
@Entity(tableName = "portfolio_transaction", indices = [Index(value = ["asset_id", "executed_at"]), Index(value = ["executed_at"]), Index(value = ["location_id"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["location_id"], onDelete = ForeignKey.RESTRICT)])
data class TransactionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, @ColumnInfo(name = "transaction_type") val transactionType: String, val quantity: BigDecimal, @ColumnInfo(name = "price_per_unit") val pricePerUnit: BigDecimal? = null, val fee: BigDecimal? = null, @ColumnInfo(name = "transaction_currency") val transactionCurrency: String = PortfolioCodes.IRT, @ColumnInfo(name = "location_id") val locationId: Long? = null, @ColumnInfo(name = "executed_at") val executedAt: Instant, val notes: String = "", @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(), @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now())
@Entity(tableName = "portfolio_assetprice", indices = [Index(value = ["asset_id", "captured_at"]), Index(value = ["provider", "captured_at"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE)])
data class AssetPriceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, val price: BigDecimal, val currency: String, val provider: String, @ColumnInfo(name = "provider_symbol") val providerSymbol: String = "", @ColumnInfo(name = "captured_at") val capturedAt: Instant, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now())
@Entity(tableName = "portfolio_assetvaluation", indices = [Index(value = ["asset_id", "captured_at"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE)])
data class AssetValuationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, val quantity: BigDecimal, @ColumnInfo(name = "usdt_value") val usdtValue: BigDecimal?, @ColumnInfo(name = "toman_value") val tomanValue: BigDecimal?, @ColumnInfo(name = "captured_at") val capturedAt: Instant, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now())
/** An immutable total used directly by the portfolio history chart. */
@Entity(tableName = "portfolio_snapshot", indices = [Index(value = ["captured_at"])])
data class PortfolioSnapshotEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "usdt_value") val usdtValue: BigDecimal, @ColumnInfo(name = "toman_value") val tomanValue: BigDecimal, @ColumnInfo(name = "captured_at") val capturedAt: Instant, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now())
@Entity(tableName = "portfolio_providerrefresh", indices = [Index(value = ["provider", "started_at"])])
data class ProviderRefreshEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val provider: String, @ColumnInfo(name = "started_at") val startedAt: Instant, @ColumnInfo(name = "finished_at") val finishedAt: Instant? = null, val status: String, @ColumnInfo(name = "error_message") val errorMessage: String = "")

fun AssetEntity.hasSamePricingAs(other: AssetEntity): Boolean =
    pricingMode == other.pricingMode && if (pricingMode == PortfolioCodes.MANUAL) {
        manualPriceCurrency.equals(other.manualPriceCurrency, ignoreCase = true)
    } else {
        priceProvider.equals(other.priceProvider, ignoreCase = true) &&
            providerSymbol.equals(other.providerSymbol, ignoreCase = true)
    }

@Dao interface PortfolioDao {
    @Query("SELECT * FROM portfolio_asset ORDER BY name") fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM portfolio_asset WHERE id=:id") fun observeAsset(id: Long): Flow<AssetEntity?>
    @Query("SELECT * FROM portfolio_asset WHERE id=:id") suspend fun asset(id: Long): AssetEntity?
    @Query("SELECT * FROM portfolio_asset WHERE is_active=1 ORDER BY name") suspend fun activeAssets(): List<AssetEntity>
    @Query("SELECT * FROM portfolio_asset") suspend fun allAssets(): List<AssetEntity>
    @Insert suspend fun insertAsset(asset: AssetEntity): Long
    @Update suspend fun updateAsset(asset: AssetEntity)
    @Delete suspend fun deleteAsset(asset: AssetEntity)
    @Query("DELETE FROM portfolio_transaction WHERE asset_id = :assetId") suspend fun deleteTransactionsForAsset(assetId: Long)
    @Transaction
    suspend fun deleteAssetAndTransactions(asset: AssetEntity) {
        deleteTransactionsForAsset(asset.id)
        deleteAsset(asset)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAssetTags(items: List<AssetTagCrossRef>)
    @Query("DELETE FROM portfolio_asset_tags WHERE asset_id=:assetId") suspend fun clearAssetTags(assetId: Long)
    @Query("SELECT tag_id FROM portfolio_asset_tags WHERE asset_id=:assetId") fun observeAssetTagIds(assetId: Long): Flow<List<Long>>
    @Query("SELECT * FROM portfolio_tag ORDER BY name") fun observeTags(): Flow<List<TagEntity>>
    @Insert suspend fun insertTag(tag: TagEntity): Long
    @Query("SELECT * FROM portfolio_location ORDER BY name") fun observeLocations(): Flow<List<LocationEntity>>
    @Insert suspend fun insertLocation(location: LocationEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLocations(locations: List<LocationEntity>)
    @Query("SELECT * FROM portfolio_transaction WHERE asset_id=:assetId ORDER BY executed_at, id") suspend fun transactions(assetId: Long): List<TransactionEntity>
    @Query("SELECT * FROM portfolio_transaction WHERE id=:id") suspend fun transaction(id: Long): TransactionEntity?
    @Query("SELECT * FROM portfolio_transaction WHERE asset_id=:assetId ORDER BY executed_at, id") fun observeTransactionsForAsset(assetId: Long): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM portfolio_transaction ORDER BY executed_at DESC, id DESC") fun observeTransactions(): Flow<List<TransactionEntity>>
    @Insert suspend fun insertTransaction(transaction: TransactionEntity): Long
    @Update suspend fun updateTransaction(transaction: TransactionEntity)
    @Delete suspend fun deleteTransaction(transaction: TransactionEntity)
    @Query("SELECT * FROM portfolio_assetprice WHERE asset_id=:assetId ORDER BY captured_at DESC, id DESC") suspend fun prices(assetId: Long): List<AssetPriceEntity>
    @Query("SELECT * FROM portfolio_assetprice WHERE asset_id=:assetId ORDER BY captured_at DESC, id DESC") fun observePrices(assetId: Long): Flow<List<AssetPriceEntity>>
    @Query("SELECT * FROM portfolio_assetprice") fun observeAllPrices(): Flow<List<AssetPriceEntity>>
    @Insert suspend fun insertPrice(price: AssetPriceEntity): Long
    @Query("DELETE FROM portfolio_assetprice WHERE asset_id=:assetId") suspend fun deletePricesForAsset(assetId: Long)
    /** Discard responses fetched before the user changed the market selection. */
    @Transaction
    suspend fun insertPriceForConfiguration(expectedAsset: AssetEntity, price: AssetPriceEntity): Long? {
        val current = asset(expectedAsset.id) ?: return null
        if (!current.hasSamePricingAs(expectedAsset)) return null
        return insertPrice(price)
    }
    @Query("SELECT * FROM portfolio_providerrefresh ORDER BY started_at DESC LIMIT 20") fun observeRefreshes(): Flow<List<ProviderRefreshEntity>>
    @Insert suspend fun insertRefresh(refresh: ProviderRefreshEntity): Long
    @Update suspend fun updateRefresh(refresh: ProviderRefreshEntity)
    @Insert suspend fun insertValuation(item: AssetValuationEntity): Long
    @Query("SELECT * FROM portfolio_assetvaluation") fun observeValuations(): Flow<List<AssetValuationEntity>>
    @Query("SELECT * FROM portfolio_assetvaluation") suspend fun valuations(): List<AssetValuationEntity>
    @Insert suspend fun insertPortfolioSnapshot(item: PortfolioSnapshotEntity): Long
    @Query("SELECT * FROM portfolio_snapshot ORDER BY captured_at ASC, id ASC") fun observePortfolioSnapshots(): Flow<List<PortfolioSnapshotEntity>>
    @Query("SELECT * FROM portfolio_snapshot ORDER BY captured_at ASC, id ASC") suspend fun portfolioSnapshots(): List<PortfolioSnapshotEntity>
    @Query("SELECT * FROM portfolio_snapshot ORDER BY captured_at DESC, id DESC LIMIT 1") suspend fun latestPortfolioSnapshot(): PortfolioSnapshotEntity?
    /** Publish the per-asset records and their total as one coherent history point. */
    @Transaction
    suspend fun insertValuationSnapshot(
        valuations: List<AssetValuationEntity>,
        snapshot: PortfolioSnapshotEntity,
    ) {
        for (valuation in valuations) insertValuation(valuation)
        insertPortfolioSnapshot(snapshot)
    }
    @Query("SELECT * FROM portfolio_assetvaluation WHERE asset_id=:assetId ORDER BY captured_at DESC, id DESC LIMIT 1") suspend fun latestValuation(assetId: Long): AssetValuationEntity?
    @Query("SELECT * FROM portfolio_assetvaluation WHERE asset_id=:assetId ORDER BY captured_at ASC") fun observeValuations(assetId: Long): Flow<List<AssetValuationEntity>>
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `widget_configuration` (
                `app_widget_id` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `hide_total` INTEGER NOT NULL,
                `market_provider` TEXT,
                `market_provider_symbol` TEXT,
                `market_display_name` TEXT,
                `market_symbol` TEXT,
                `market_asset_type` TEXT,
                `revision` INTEGER NOT NULL,
                PRIMARY KEY(`app_widget_id`)
            )""".trimIndent(),
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `widget_quote` (
                `provider` TEXT NOT NULL,
                `provider_symbol` TEXT NOT NULL,
                `price` TEXT,
                `currency` TEXT,
                `quote_at` INTEGER,
                `last_success_at` INTEGER,
                `last_attempt_at` INTEGER,
                `status` TEXT NOT NULL,
                `last_error` TEXT,
                PRIMARY KEY(`provider`, `provider_symbol`)
            )""".trimIndent(),
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TEMP TABLE widget_market_selection_migration AS
                SELECT app_widget_id, market_provider, market_provider_symbol,
                       market_display_name, market_symbol, market_asset_type
                FROM widget_configuration
                WHERE kind = 'ASSET_PRICE'
                  AND market_provider IS NOT NULL
                  AND market_provider_symbol IS NOT NULL
                  AND market_display_name IS NOT NULL
                  AND market_symbol IS NOT NULL
                  AND market_asset_type IS NOT NULL""".trimIndent(),
        )
        database.execSQL(
            """CREATE TABLE widget_configuration_new (
                app_widget_id INTEGER NOT NULL,
                kind TEXT NOT NULL,
                currency TEXT NOT NULL,
                hide_total INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                PRIMARY KEY(app_widget_id)
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO widget_configuration_new
                (app_widget_id, kind, currency, hide_total, revision)
                SELECT app_widget_id, kind, currency, hide_total, revision
                FROM widget_configuration""".trimIndent(),
        )
        database.execSQL("DROP TABLE widget_configuration")
        database.execSQL("ALTER TABLE widget_configuration_new RENAME TO widget_configuration")
        database.execSQL(
            """CREATE TABLE widget_market_selection (
                selection_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                app_widget_id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                provider TEXT NOT NULL,
                provider_symbol TEXT NOT NULL,
                display_name TEXT NOT NULL,
                symbol TEXT NOT NULL,
                asset_type TEXT NOT NULL,
                FOREIGN KEY(app_widget_id) REFERENCES widget_configuration(app_widget_id)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )""".trimIndent(),
        )
        database.execSQL(
            """INSERT INTO widget_market_selection
                (app_widget_id, position, provider, provider_symbol, display_name, symbol, asset_type)
                SELECT app_widget_id, 0, market_provider, market_provider_symbol,
                       market_display_name, market_symbol, market_asset_type
                FROM widget_market_selection_migration""".trimIndent(),
        )
        database.execSQL("DROP TABLE widget_market_selection_migration")
        database.execSQL(
            "CREATE INDEX index_widget_market_selection_app_widget_id ON widget_market_selection(app_widget_id)",
        )
        database.execSQL(
            "CREATE INDEX index_widget_market_selection_provider_provider_symbol ON widget_market_selection(provider, provider_symbol)",
        )
        database.execSQL(
            "CREATE UNIQUE INDEX index_widget_market_selection_app_widget_id_provider_provider_symbol ON widget_market_selection(app_widget_id, provider, provider_symbol)",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE widget_quote ADD COLUMN previous_price TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE widget_quote ADD COLUMN change_percent TEXT")
    }
}

@Database(entities = [TagEntity::class, LocationEntity::class, AssetEntity::class, AssetTagCrossRef::class, TransactionEntity::class, AssetPriceEntity::class, AssetValuationEntity::class, PortfolioSnapshotEntity::class, ProviderRefreshEntity::class, WidgetConfigurationEntity::class, WidgetMarketSelectionEntity::class, WidgetQuoteEntity::class], version = 7, exportSchema = true)
@TypeConverters(DbConverters::class)
abstract class PortfolioDatabase : RoomDatabase() {
    abstract fun dao(): PortfolioDao
    abstract fun widgetDao(): WidgetDao
}
