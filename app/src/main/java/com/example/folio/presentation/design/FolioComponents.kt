package com.example.folio.presentation.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolioPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showHeading: Boolean = true,
    actionAlignment: Alignment.Horizontal = Alignment.End,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FolioSpacing.large, vertical = FolioSpacing.large),
    ) {
        if (showHeading) {
            Column {
                subtitle?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(FolioSpacing.small))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FolioSpacing.medium, actionAlignment),
                verticalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            ) {
                actions()
            }
        }
        Spacer(Modifier.height(FolioSpacing.extraLarge))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioTopBar(
    title: String,
    onBack: () -> Unit,
    backDescription: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            FolioIconAction(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = backDescription,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
fun FolioPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = FolioMinimumTouchTarget),
        enabled = enabled,
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(FolioIconSize.medium))
            Spacer(Modifier.size(FolioSpacing.small))
        }
        Text(text)
    }
}

@Composable
fun FolioSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = FolioMinimumTouchTarget),
        enabled = enabled,
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(FolioIconSize.medium))
            Spacer(Modifier.size(FolioSpacing.small))
        }
        Text(text)
    }
}

@Composable
fun FolioDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = FolioMinimumTouchTarget),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(text)
    }
}

@Composable
fun FolioIconAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(
            minWidth = FolioMinimumTouchTarget,
            minHeight = FolioMinimumTouchTarget,
        ),
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(FolioIconSize.large),
        )
    }
}

@Composable
fun FolioCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = FolioShapeTokens.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = FolioElevation.level0,
            pressedElevation = FolioElevation.level1,
            focusedElevation = FolioElevation.level1,
            hoveredElevation = FolioElevation.level2,
            draggedElevation = FolioElevation.level2,
        ),
    ) {
        Column(
            modifier = Modifier.padding(FolioSpacing.large),
            content = content,
        )
    }
}

@Composable
fun FolioListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier
            .then(clickableModifier)
            .heightIn(min = FolioMinimumTouchTarget)
            .padding(horizontal = FolioSpacing.medium, vertical = FolioSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FolioSpacing.medium),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FolioSpacing.extraSmall),
            content = content,
        )
        trailing?.invoke()
    }
}

@Composable
fun FolioFinancialValue(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    isolateDirection: Boolean = true,
) {
    Text(
        text = if (isolateDirection) isolateFinancialText(value) else value,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun FolioField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        supportingText = supportingText?.let { message -> { Text(message) } },
        isError = isError,
        singleLine = singleLine,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FolioChoiceChips(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    optionLabel: @Composable (T) -> String = { it.toString() },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FolioSpacing.small),
            verticalArrangement = Arrangement.spacedBy(FolioSpacing.small),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) },
                    modifier = Modifier.heightIn(min = FolioMinimumTouchTarget),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FolioSelector(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optionLabel: @Composable (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = optionLabel(selected)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        FolioField(
            value = selectedLabel,
            onValueChange = {},
            label = label,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    modifier = Modifier.heightIn(min = FolioMinimumTouchTarget),
                )
            }
        }
    }
}

@Composable
fun FolioDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    supportingError: String? = null,
    working: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FolioSpacing.small)) {
                Text(text)
                supportingError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(FolioIconSize.large),
                    )
                }
            }
        },
        confirmButton = {
            if (destructive) {
                FolioDestructiveButton(confirmText, onConfirm, enabled = !working)
            } else {
                FolioPrimaryButton(confirmText, onConfirm, enabled = !working)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = FolioMinimumTouchTarget),
                enabled = !working,
            ) {
                Text(dismissText)
            }
        },
    )
}

enum class FolioMessageKind {
    Loading,
    Error,
    Empty,
    Info,
}

@Composable
fun FolioStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    kind: FolioMessageKind = FolioMessageKind.Empty,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val semanticColors = LocalFolioSemanticColors.current
    val messageColor = when (kind) {
        FolioMessageKind.Error -> MaterialTheme.colorScheme.error
        FolioMessageKind.Loading -> MaterialTheme.colorScheme.primary
        FolioMessageKind.Info -> semanticColors.warning
        FolioMessageKind.Empty -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FolioSpacing.small),
    ) {
        if (kind == FolioMessageKind.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(FolioIconSize.large),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        title?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(message, color = messageColor, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            FolioSecondaryButton(actionLabel, onAction)
        }
    }
}
