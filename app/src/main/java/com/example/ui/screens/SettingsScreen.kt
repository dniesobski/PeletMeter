package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.GitHubUpdateChecker
import com.example.data.UpdateCheckState
import com.example.domain.StockSummary
import com.example.model.CalculationMethod
import com.example.model.CalendarDay
import com.example.model.PelletStockBatch
import com.example.model.UserSettings
import com.example.ui.components.formatKg
import com.example.ui.components.formatNumber
import com.example.ui.components.formatPln

private enum class StockPriceMode {
    PER_BAG,
    TOTAL_BATCH
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    stockBatches: List<PelletStockBatch>,
    stockSummary: StockSummary,
    updateCheckState: UpdateCheckState,
    userEmail: String?,
    userDisplayName: String?,
    isMutating: Boolean,
    onSaveSettings: (bagCapacityKg: Double, method: CalculationMethod) -> Unit,
    onAddStockBatch: (
        bagsCount: Double,
        bagCapacityKg: Double,
        pricePerBagPln: Double,
        purchaseDateEpochDay: Long,
        purchasePlace: String?,
        pelletType: String?,
        note: String?
    ) -> Unit,
    onDeleteStockBatch: (String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val activeMethod = settings.resolvedMethod()
    var bagCapacityText by remember(settings.bagCapacityKg) {
        mutableStateOf(
            if (settings.bagCapacityKg % 1.0 == 0.0) {
                settings.bagCapacityKg.toInt().toString()
            } else {
                settings.bagCapacityKg.toString().replace('.', ',')
            }
        )
    }
    var bagError by remember { mutableStateOf<String?>(null) }

    // Stock batch form state
    var stockDateEpochDay by remember { mutableLongStateOf(CalendarDay.today().toEpochDay()) }
    var showStockDatePicker by remember { mutableStateOf(false) }
    var stockPlaceText by remember { mutableStateOf("") }
    var stockTypeText by remember { mutableStateOf("") }
    var stockBagsText by remember { mutableStateOf("65") }
    var stockPriceMode by remember { mutableStateOf(StockPriceMode.PER_BAG) }
    var stockPriceText by remember { mutableStateOf("") }
    var stockNoteText by remember { mutableStateOf("") }
    var stockError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.bagCapacityKg) {
        bagCapacityText = if (settings.bagCapacityKg % 1.0 == 0.0) {
            settings.bagCapacityKg.toInt().toString()
        } else {
            settings.bagCapacityKg.toString().replace('.', ',')
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            )
        }

        // Owned Pellet Stock & Purchase Price Section
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.settings_stock_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_stock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (stockSummary.hasPriceData) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_stock_summary_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.settings_stock_avg_price,
                                        formatPln(stockSummary.weightedAvgPricePerBag),
                                        formatPln(stockSummary.weightedAvgPricePerKg)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.settings_stock_remaining,
                                        formatNumber(stockSummary.remainingBags),
                                        formatKg(stockSummary.remainingKg)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(
                                        R.string.settings_stock_purchased,
                                        formatNumber(stockSummary.totalPurchasedBags),
                                        formatKg(stockSummary.totalPurchasedKg)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.settings_stock_date_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showStockDatePicker = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("stock_date_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(CalendarDay.fromEpochDay(stockDateEpochDay).toDisplayString())
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = stockPlaceText,
                        onValueChange = { if (it.length <= 120) stockPlaceText = it },
                        label = { Text(stringResource(R.string.settings_stock_place_label)) },
                        placeholder = { Text(stringResource(R.string.settings_stock_place_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_place_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = stockTypeText,
                        onValueChange = { if (it.length <= 120) stockTypeText = it },
                        label = { Text(stringResource(R.string.settings_stock_type_label)) },
                        placeholder = { Text(stringResource(R.string.settings_stock_type_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_type_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Sosnowy", "Świerkowy", "Dębowy", "Bukowy", "Mieszany").forEach { kind ->
                            val selected = stockTypeText.equals(kind, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    stockTypeText = if (selected) "" else kind
                                },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                label = { Text(kind) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = stockBagsText,
                        onValueChange = { next ->
                            if (next.length <= 8) {
                                stockBagsText = next
                                stockError = null
                            }
                        },
                        label = { Text(stringResource(R.string.settings_stock_bags_label)) },
                        suffix = { Text("work. (po ${formatKg(settings.bagCapacityKg)} kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_bags_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("10", "20", "40", "65").forEach { preset ->
                            AssistChip(
                                onClick = {
                                    stockBagsText = preset
                                    stockError = null
                                },
                                label = { Text("$preset work.") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = stockPriceMode == StockPriceMode.PER_BAG,
                            onClick = {
                                stockPriceMode = StockPriceMode.PER_BAG
                                stockError = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("stock_price_mode_per_bag")
                        ) {
                            Text("Za 1 worek")
                        }
                        SegmentedButton(
                            selected = stockPriceMode == StockPriceMode.TOTAL_BATCH,
                            onClick = {
                                stockPriceMode = StockPriceMode.TOTAL_BATCH
                                stockError = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("stock_price_mode_total")
                        ) {
                            Text("Za całość")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = stockPriceText,
                        onValueChange = { next ->
                            if (next.length <= 10) {
                                stockPriceText = next
                                stockError = null
                            }
                        },
                        label = {
                            Text(
                                if (stockPriceMode == StockPriceMode.PER_BAG) {
                                    stringResource(R.string.settings_stock_price_per_bag)
                                } else {
                                    stringResource(R.string.settings_stock_price_total)
                                }
                            )
                        },
                        suffix = { Text("zł") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_price_input")
                    )

                    val previewBags = stockBagsText.replace(',', '.').toDoubleOrNull()
                    val previewPrice = stockPriceText.replace(',', '.').toDoubleOrNull()
                    if (previewBags != null && previewBags > 0.0 && previewPrice != null && previewPrice >= 0.0) {
                        val pricePerBag = if (stockPriceMode == StockPriceMode.PER_BAG) {
                            previewPrice
                        } else {
                            previewPrice / previewBags
                        }
                        val totalCost = pricePerBag * previewBags
                        val totalKg = previewBags * settings.bagCapacityKg
                        val pricePerKg = if (totalKg > 0) totalCost / totalKg else 0.0
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Razem: ${formatPln(totalCost)} (${formatPln(pricePerBag)}/worek • ${formatPln(pricePerKg)}/kg)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = stockNoteText,
                        onValueChange = { if (it.length <= 120) stockNoteText = it },
                        label = { Text(stringResource(R.string.settings_stock_note_label)) },
                        placeholder = { Text(stringResource(R.string.settings_stock_note_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_note_input")
                    )

                    if (stockError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stockError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val invalidStockMsg = stringResource(R.string.settings_stock_invalid)
                    Button(
                        onClick = {
                            val bags = stockBagsText.replace(',', '.').toDoubleOrNull()
                            val rawPrice = stockPriceText.replace(',', '.').toDoubleOrNull()
                            if (bags == null || bags <= 0.0 || bags > 10000.0 || rawPrice == null || rawPrice < 0.0) {
                                stockError = invalidStockMsg
                            } else {
                                val pricePerBag = if (stockPriceMode == StockPriceMode.PER_BAG) {
                                    rawPrice
                                } else {
                                    rawPrice / bags
                                }
                                val totalKg = bags * settings.bagCapacityKg
                                val totalCost = bags * pricePerBag
                                if (pricePerBag > 100000.0 || totalKg > 100000.0 || totalCost > 10000000.0) {
                                    stockError = invalidStockMsg
                                } else {
                                    onAddStockBatch(
                                        bags,
                                        settings.bagCapacityKg,
                                        pricePerBag,
                                        stockDateEpochDay,
                                        stockPlaceText.trim().takeIf { it.isNotEmpty() },
                                        stockTypeText.trim().takeIf { it.isNotEmpty() },
                                        stockNoteText.trim().takeIf { it.isNotEmpty() }
                                    )
                                    stockPriceText = ""
                                    stockPlaceText = ""
                                    stockTypeText = ""
                                    stockNoteText = ""
                                    stockError = null
                                }
                            }
                        },
                        enabled = !isMutating,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("add_stock_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_stock_add_button))
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (stockBatches.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_stock_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stockBatches.forEach { batch ->
                                StockBatchRow(
                                    batch = batch,
                                    onDelete = { onDeleteStockBatch(batch.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Calculation Method Section
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.settings_method_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    CalculationMethodOptionCard(
                        title = stringResource(R.string.settings_method_full_title),
                        description = stringResource(R.string.settings_method_full_desc),
                        selected = activeMethod == CalculationMethod.FULL_REFILL,
                        onSelect = {
                            onSaveSettings(settings.bagCapacityKg, CalculationMethod.FULL_REFILL)
                        },
                        modifier = Modifier.testTag("method_full_refill_option")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CalculationMethodOptionCard(
                        title = stringResource(R.string.settings_method_direct_title),
                        description = stringResource(R.string.settings_method_direct_desc),
                        selected = activeMethod == CalculationMethod.DIRECT_SUM,
                        onSelect = {
                            onSaveSettings(settings.bagCapacityKg, CalculationMethod.DIRECT_SUM)
                        },
                        modifier = Modifier.testTag("method_direct_sum_option")
                    )
                }
            }
        }

        // Bag Capacity Section
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.settings_bag_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_bag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(15.0, 20.0, 25.0).forEach { preset ->
                            val isSelected = kotlin.math.abs(settings.bagCapacityKg - preset) < 0.01
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    bagError = null
                                    bagCapacityText = preset.toInt().toString()
                                    onSaveSettings(preset, activeMethod)
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                label = { Text("${formatKg(preset)} kg") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bagCapacityText,
                        onValueChange = { next ->
                            if (next.length <= 6) {
                                bagCapacityText = next
                                bagError = null
                            }
                        },
                        label = { Text(stringResource(R.string.settings_bag_label)) },
                        suffix = { Text("kg") },
                        singleLine = true,
                        isError = bagError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bag_capacity_input")
                    )

                    if (bagError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = bagError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val invalidBagMsg = stringResource(R.string.settings_bag_invalid)
                    Button(
                        onClick = {
                            val parsed = bagCapacityText.replace(',', '.').toDoubleOrNull()
                            if (parsed == null || parsed < 1.0 || parsed > 100.0) {
                                bagError = invalidBagMsg
                            } else {
                                onSaveSettings(parsed, activeMethod)
                            }
                        },
                        enabled = !isMutating,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_bag_capacity_button")
                    ) {
                        Text(stringResource(R.string.settings_bag_save))
                    }
                }
            }
        }

        // GitHub App Update Section
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.settings_update_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_update_repo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_update_current_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when (updateCheckState) {
                        UpdateCheckState.Idle -> Unit
                        UpdateCheckState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_update_checking),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        is UpdateCheckState.UpdateAvailable -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_update_available_title,
                                            updateCheckState.latestVersion
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (updateCheckState.releaseNotes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = updateCheckState.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val targetUrl = updateCheckState.apkDownloadUrl ?: updateCheckState.releaseUrl
                                    Button(
                                        onClick = { runCatching { uriHandler.openUri(targetUrl) } },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("download_github_update_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (updateCheckState.apkDownloadUrl != null) {
                                                stringResource(R.string.settings_update_download_apk)
                                            } else {
                                                stringResource(R.string.settings_update_open_release)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateCheckState.UpToDate -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_update_up_to_date,
                                        updateCheckState.latestTag ?: updateCheckState.currentVersion
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        is UpdateCheckState.NoReleases -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_update_no_releases,
                                            updateCheckState.latestCommitShort ?: "brak"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!updateCheckState.latestCommitMessage.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = updateCheckState.latestCommitMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        is UpdateCheckState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = updateCheckState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onCheckForUpdates,
                            enabled = updateCheckState !is UpdateCheckState.Checking,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("check_github_updates_button")
                        ) {
                            Text(stringResource(R.string.settings_update_check_button))
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching { uriHandler.openUri(GitHubUpdateChecker.REPO_RELEASES_URL) }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("open_github_repo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = stringResource(R.string.settings_update_open_repo),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub")
                        }
                    }
                }
            }
        }

        // Google Account Section
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.settings_account_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = userDisplayName?.takeIf { it.isNotBlank() } ?: "Użytkownik Google",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_sign_out))
                    }
                }
            }
        }
    }

    if (showStockDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CalendarDay.fromEpochDay(stockDateEpochDay).toUtcMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStockDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            stockDateEpochDay = CalendarDay.fromUtcMillis(millis).toEpochDay()
                        }
                        showStockDatePicker = false
                    }
                ) {
                    Text("Wybierz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStockDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun StockBatchRow(
    batch: PelletStockBatch,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_batch_row_${batch.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatNumber(batch.bagsCount)} work. (${formatKg(batch.totalKg)} kg)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatPln(batch.pricePerBagPln)}/worek • Łącznie ${formatPln(batch.totalCostPln)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val dateLabel = batch.purchaseDateEpochDay?.let {
                    CalendarDay.fromEpochDay(it).toShortDisplayString()
                }
                val detailParts = listOfNotNull(
                    dateLabel,
                    batch.pelletType?.takeIf { it.isNotBlank() },
                    batch.purchasePlace?.takeIf { it.isNotBlank() }
                )
                if (detailParts.isNotEmpty()) {
                    Text(
                        text = detailParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!batch.note.isNullOrBlank()) {
                    Text(
                        text = batch.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("delete_stock_batch_${batch.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CalculationMethodOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(14.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
