package com.example.folio.data.widget

class WidgetRepository(
    private val dao: WidgetDao,
) {
    suspend fun configuration(appWidgetId: Int): WidgetConfiguration? =
        dao.configuration(appWidgetId)?.toModel()

    suspend fun configurations(): List<WidgetConfiguration> =
        dao.configurations().map(WidgetConfigurationEntity::toModel)

    suspend fun save(configuration: WidgetConfiguration): WidgetConfiguration {
        configuration.validate()
        return dao.saveConfiguration(configuration.toEntity()).toModel()
    }

    suspend fun setTotalHidden(appWidgetId: Int, hidden: Boolean): WidgetConfiguration =
        dao.setTotalHidden(appWidgetId, hidden).toModel()

    suspend fun remove(appWidgetIds: Set<Int>) {
        require(appWidgetIds.all { it > 0 }) { "Widget ids must be positive." }
        dao.removeConfigurationsAndPrune(appWidgetIds)
    }

    suspend fun remapIds(oldToNew: Map<Int, Int>) {
        dao.remapConfigurationIds(oldToNew)
    }

    suspend fun quote(key: MarketKey): WidgetQuote? {
        key.validate()
        return dao.quote(key.provider, key.providerSymbol)?.toModel()
    }

    suspend fun storeQuote(quote: WidgetQuote) {
        quote.validate()
        dao.storeQuote(quote.toEntity())
    }

    suspend fun pruneQuotes() {
        dao.deleteUnreferencedQuotes()
    }
}
