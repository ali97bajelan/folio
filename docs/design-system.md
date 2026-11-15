# Folio design system

Folio follows the Android system light or dark setting with brand-authored Material 3 colors. The app and Glance widgets share the same semantic palette. There is no separate in-app theme setting.

## Color roles

| Role | Light | Dark |
| --- | --- | --- |
| Background | `#F5F7FB` | `#0B1020` |
| Surface | `#FFFFFF` | `#131B2B` |
| Raised surface | `#EEF2F9` | `#172137` |
| Main text | `#172033` | `#F4F6FC` |
| Muted text | `#4B5B73` | `#B9C4D6` |
| Border | `#D4DCE8` | `#2C3950` |
| Primary | `#315FC8` | `#668FFC` |
| Gain | `#087F5D` | `#76D8B0` |
| Loss | `#B42336` | `#F47783` |
| Warning | `#885D00` | `#FFC857` |
| Disabled | `#929BAD` | `#788297` |

The eight allocation series colors are shared by Compose charts and widgets: aqua `#76D8B0`, blue `#668FFC`, yellow `#FFC857`, red `#F47783`, purple `#8B7CF6`, cyan `#5BC0EB`, orange `#E28F63`, and teal `#5FADB7`.

## Dimensions and type

| Token group | Values |
| --- | --- |
| Spacing | 4, 8, 12, 16, 24, 32 dp |
| Icon sizes | 16, 20, 24 dp |
| Corner radii | 8, 12, 16 dp, and pill |
| Surface elevation | 0, 1, 3 dp |
| Interactive targets | At least 48 dp |
| Motion | Short 150 ms; medium 250 ms |
| Typography | Material 3 `Typography` roles |

## Compose usage

Place screens inside `FolioTheme`; it selects the light or dark color scheme from the system setting:

```kotlin
@Composable
fun ExampleScreen() {
    FolioTheme {
        FolioPage(title = stringResource(R.string.assets)) {
            FolioCard {
                FolioFinancialValue(
                    value = "\$1,250.00 USD",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}
```

Use the shared field for labeled input and inline feedback:

```kotlin
FolioField(
    value = query,
    onValueChange = onQueryChanged,
    label = stringResource(R.string.market_search),
    placeholder = stringResource(R.string.market_search_hint),
    supportingText = validationMessage,
    isError = hasValidationError,
    singleLine = true,
)
```

`FolioFinancialValue` isolates Latin financial text for mixed RTL/LTR content by default. Use `isolateFinancialText(value)` when passing a financial or provider value to another text component. Keep number and currency formatting in the existing formatter.

## Glance usage

Wrap each widget layout with `FolioWidgetTheme`. It adapts both Material 3 schemes to day/night `ColorProvider` values while keeping the widget UI native to Glance:

```kotlin
FolioWidgetTheme {
    Text(
        text = amount,
        style = TextStyle(color = GlanceTheme.colors.onSurface),
    )
}
```

Use `LocalFolioWidgetSemanticColors.current.allocationSeries` for allocation bars and `FolioWidgetSpacing` for shared widget content insets and 48 dp action targets. Keep widget-only compact thresholds and launcher sizing in the Glance layout.
