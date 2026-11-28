# Folio design system

Folio uses one light and dark palette across the Compose app, Glance widgets, Android theme resources, and widget picker previews. Compose and Glance keep native UI components; both draw from the shared tokens in `FolioTokens.kt`.

The app supports Dark, Light, and System appearance. The selected appearance is also applied to both widgets through `FolioWidgetTheme`.

## Color roles

| Role | Light | Dark |
| --- | --- | --- |
| Background and surface | `#F7F8FA` / `#FFFFFF` | `#051424` |
| Surface variant | `#F0F2F5` | `#122131` |
| Main text | `#171C25` | `#D4E4FA` |
| Muted text | `#555D67` | `#D8C3AD` |
| Primary | `#855300` | `#FFC174` |
| Secondary | `#006B4F` | `#4EDEA3` |
| Gain / container | `#006B4F` / `#D8F3E8` | `#4EDEA3` / `#123F38` |
| Loss / container | `#B3261E` / `#F9E1E4` | `#FFB4AB` / `#4B2634` |
| Warning / container | `#855300` / `#FFEAC2` | `#FFC174` / `#44371F` |
| Border | `#E3DAD0` | `#273647` |
| Disabled | `#79716A` | `#A08E7A` |

The eight allocation colors are shared between charts, widgets, and previews: `#4EDEA3`, `#FFC174`, `#C7C8FF`, `#D8C3AD`, `#5BC0EB`, `#E28F63`, `#5FADB7`, and `#9B8CFF`.

## Dimensions and type

| Token group | Values |
| --- | --- |
| Compose spacing | 4, 8, 12, 16, 24, 32 dp |
| Compose icon sizes | 16, 20, 24 dp |
| Compose corner radii | 8, 12, 16 dp, and pill |
| Surface elevation | 0, 1, 3 dp |
| Minimum interactive target | 48 dp |
| Motion | Short 150 ms; medium 250 ms |
| Compose typography | Material 3 `Typography` roles |
| Glance typography and widget geometry | Named roles in `FolioWidgetTokens` |

Widget-specific roles cover action targets and icons, card and row corners, compact and expanded text sizes, row spacing, and allocation bars. Width and height breakpoints remain in each widget layout because they describe responsive behavior rather than a reusable visual style.

## Compose

`FolioTheme` maps the selected appearance to `FolioLightColorScheme` or `FolioDarkColorScheme`, `FolioTypography`, `FolioShapes`, and the matching semantic colors. Use `MaterialTheme` for component styling and `LocalFolioSemanticColors` for gain, loss, warning, disabled, border, and allocation roles.

## Glance widgets

`FolioWidgetTheme` adapts the same color and semantic tokens to Glance `ColorProvider` values. Widget layouts use `FolioWidgetTokens` for native Glance dimensions and type sizes while preserving their responsive breakpoints and financial text formatting.

Android XML theme colors and widget picker previews mirror these roles in `values/colors.xml` and `values-night/colors.xml`; keep those resource palettes aligned when changing Folio tokens. Static picker previews use system light/dark resource qualifiers, while live widgets follow the app's selected appearance.
