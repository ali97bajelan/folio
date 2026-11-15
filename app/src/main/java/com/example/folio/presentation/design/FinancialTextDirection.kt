package com.example.folio.presentation.design

private const val LEFT_TO_RIGHT_ISOLATE = "\u2066"
private const val POP_DIRECTIONAL_ISOLATE = "\u2069"

/** Wraps Latin financial notation so it stays in natural reading order inside RTL text. */
fun isolateFinancialText(value: String): String =
    "$LEFT_TO_RIGHT_ISOLATE$value$POP_DIRECTIONAL_ISOLATE"
