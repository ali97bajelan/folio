package com.example.folio.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
data class AssetEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val symbol: String, @ColumnInfo(name = "asset_type") val assetType: String, val unit: String = "UNIT", @ColumnInfo(name = "pricing_mode") val pricingMode: String = "MARKET", @ColumnInfo(name = "price_provider") val priceProvider: String = "", @ColumnInfo(name = "provider_symbol") val providerSymbol: String = "", @ColumnInfo(name = "manual_price_currency") val manualPriceCurrency: String = "IRT", @ColumnInfo(name = "is_active") val isActive: Boolean = true, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(), @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now())
@Entity(tableName = "portfolio_asset_tags", indices = [Index(value = ["asset_id", "tag_id"], unique = true), Index(value = ["asset_id"]), Index(value = ["tag_id"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE)])
data class AssetTagCrossRef(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, @ColumnInfo(name = "tag_id") val tagId: Long)
@Entity(tableName = "portfolio_transaction", indices = [Index(value = ["asset_id", "executed_at"]), Index(value = ["executed_at"]), Index(value = ["location_id"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["location_id"], onDelete = ForeignKey.RESTRICT)])
data class TransactionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, @ColumnInfo(name = "transaction_type") val transactionType: String, val quantity: BigDecimal, @ColumnInfo(name = "price_per_unit") val pricePerUnit: BigDecimal? = null, val fee: BigDecimal? = null, @ColumnInfo(name = "transaction_currency") val transactionCurrency: String = "IRT", @ColumnInfo(name = "location_id") val locationId: Long? = null, @ColumnInfo(name = "executed_at") val executedAt: Instant, val notes: String = "", @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(), @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now())
@Entity(tableName = "portfolio_assetprice", indices = [Index(value = ["asset_id", "captured_at"]), Index(value = ["provider", "captured_at"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE)])
data class AssetPriceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, val price: BigDecimal, val currency: String, val provider: String, @ColumnInfo(name = "provider_symbol") val providerSymbol: String = "", @ColumnInfo(name = "captured_at") val capturedAt: Instant, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now())
@Entity(tableName = "portfolio_assetvaluation", indices = [Index(value = ["asset_id", "captured_at"])], foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["asset_id"], onDelete = ForeignKey.CASCADE)])
data class AssetValuationEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "asset_id") val assetId: Long, val quantity: BigDecimal, @ColumnInfo(name = "usdt_value") val usdtValue: BigDecimal?, @ColumnInfo(name = "toman_value") val tomanValue: BigDecimal?, @ColumnInfo(name = "captured_at") val capturedAt: Instant, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now())
/** An immutable total used directly by the portfolio history chart. */
@Entity(tableName = "portfolio_snapshot", indices = [Index(value = ["captured_at"])])
data class PortfolioSnapshotEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "usdt_value") val usdtValue: BigDecimal, @ColumnInfo(name = "toman_value") val tomanValue: BigDecimal, @ColumnInfo(name = "captured_at") val capturedAt: Instant, @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now())
@Entity(tableName = "portfolio_providerrefresh", indices = [Index(value = ["provider", "started_at"])])
data class ProviderRefreshEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val provider: String, @ColumnInfo(name = "started_at") val startedAt: Instant, @ColumnInfo(name = "finished_at") val finishedAt: Instant? = null, val status: String, @ColumnInfo(name = "error_message") val errorMessage: String = "")

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

@Database(entities = [TagEntity::class, LocationEntity::class, AssetEntity::class, AssetTagCrossRef::class, TransactionEntity::class, AssetPriceEntity::class, AssetValuationEntity::class, PortfolioSnapshotEntity::class, ProviderRefreshEntity::class], version = 3, exportSchema = true)
@TypeConverters(DbConverters::class)
abstract class PortfolioDatabase : RoomDatabase() {
    abstract fun dao(): PortfolioDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tags RENAME TO portfolio_tag"); db.execSQL("ALTER TABLE locations RENAME TO portfolio_location"); db.execSQL("ALTER TABLE assets RENAME TO portfolio_asset"); db.execSQL("ALTER TABLE transactions RENAME TO portfolio_transaction"); db.execSQL("ALTER TABLE asset_prices RENAME TO portfolio_assetprice"); db.execSQL("ALTER TABLE asset_valuations RENAME TO portfolio_assetvaluation"); db.execSQL("ALTER TABLE provider_refreshes RENAME TO portfolio_providerrefresh"); db.execSQL("ALTER TABLE asset_tags RENAME TO portfolio_asset_tags_old")
                val columns = listOf("portfolio_tag|createdAt|created_at","portfolio_tag|updatedAt|updated_at","portfolio_location|createdAt|created_at","portfolio_location|updatedAt|updated_at","portfolio_asset|assetType|asset_type","portfolio_asset|pricingMode|pricing_mode","portfolio_asset|priceProvider|price_provider","portfolio_asset|providerSymbol|provider_symbol","portfolio_asset|manualPriceCurrency|manual_price_currency","portfolio_asset|isActive|is_active","portfolio_asset|createdAt|created_at","portfolio_asset|updatedAt|updated_at","portfolio_transaction|assetId|asset_id","portfolio_transaction|transactionType|transaction_type","portfolio_transaction|pricePerUnit|price_per_unit","portfolio_transaction|transactionCurrency|transaction_currency","portfolio_transaction|locationId|location_id","portfolio_transaction|executedAt|executed_at","portfolio_transaction|createdAt|created_at","portfolio_transaction|updatedAt|updated_at","portfolio_assetprice|assetId|asset_id","portfolio_assetprice|providerSymbol|provider_symbol","portfolio_assetprice|capturedAt|captured_at","portfolio_assetprice|createdAt|created_at","portfolio_assetvaluation|assetId|asset_id","portfolio_assetvaluation|usdtValue|usdt_value","portfolio_assetvaluation|tomanValue|toman_value","portfolio_assetvaluation|capturedAt|captured_at","portfolio_assetvaluation|createdAt|created_at","portfolio_providerrefresh|startedAt|started_at","portfolio_providerrefresh|finishedAt|finished_at","portfolio_providerrefresh|errorMessage|error_message")
                columns.forEach { it.split('|').let { (table, old, new) -> db.execSQL("ALTER TABLE $table RENAME COLUMN $old TO $new") } }
                db.execSQL("CREATE TABLE IF NOT EXISTS portfolio_asset_tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, asset_id INTEGER NOT NULL, tag_id INTEGER NOT NULL, FOREIGN KEY(asset_id) REFERENCES portfolio_asset(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tag_id) REFERENCES portfolio_tag(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO portfolio_asset_tags (asset_id, tag_id) SELECT assetId, tagId FROM portfolio_asset_tags_old"); db.execSQL("DROP TABLE portfolio_asset_tags_old")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_portfolio_asset_tags_asset_id_tag_id ON portfolio_asset_tags (asset_id, tag_id)"); db.execSQL("CREATE INDEX IF NOT EXISTS index_portfolio_asset_tags_asset_id ON portfolio_asset_tags (asset_id)"); db.execSQL("CREATE INDEX IF NOT EXISTS index_portfolio_asset_tags_tag_id ON portfolio_asset_tags (tag_id)")
                listOf(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_portfolio_tag_name ON portfolio_tag (name)", "CREATE UNIQUE INDEX IF NOT EXISTS index_portfolio_location_name ON portfolio_location (name)", "CREATE UNIQUE INDEX IF NOT EXISTS index_portfolio_asset_symbol ON portfolio_asset (symbol)",
                    "CREATE INDEX IF NOT EXISTS index_portfolio_transaction_asset_id_executed_at ON portfolio_transaction (asset_id, executed_at)", "CREATE INDEX IF NOT EXISTS index_portfolio_transaction_executed_at ON portfolio_transaction (executed_at)", "CREATE INDEX IF NOT EXISTS index_portfolio_transaction_location_id ON portfolio_transaction (location_id)",
                    "CREATE INDEX IF NOT EXISTS index_portfolio_assetprice_asset_id_captured_at ON portfolio_assetprice (asset_id, captured_at)", "CREATE INDEX IF NOT EXISTS index_portfolio_assetprice_provider_captured_at ON portfolio_assetprice (provider, captured_at)",
                    "CREATE INDEX IF NOT EXISTS index_portfolio_assetvaluation_asset_id_captured_at ON portfolio_assetvaluation (asset_id, captured_at)", "CREATE INDEX IF NOT EXISTS index_portfolio_providerrefresh_provider_started_at ON portfolio_providerrefresh (provider, started_at)"
                ).forEach(db::execSQL)
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS portfolio_snapshot (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, usdt_value TEXT NOT NULL, toman_value TEXT NOT NULL, captured_at INTEGER NOT NULL, created_at INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_portfolio_snapshot_captured_at ON portfolio_snapshot (captured_at)")
            }
        }
    }
}
