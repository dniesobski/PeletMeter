package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CalendarDay
import com.example.model.PelletEntry
import com.example.model.PelletUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorSheet(
    editingEntry: PelletEntry?,
    lastEntry: PelletEntry?,
    bagCapacityKg: Double,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        dateEpochDay: Long,
        inputUnit: PelletUnit,
        inputValue: Double,
        note: String?
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialSourceEntry = editingEntry ?: lastEntry

    var selectedEpochDay by remember(editingEntry?.id) {
        mutableLongStateOf(editingEntry?.dateEpochDay ?: CalendarDay.today().toEpochDay())
    }
    var selectedUnit by remember(editingEntry?.id, lastEntry?.id) {
        mutableStateOf(initialSourceEntry?.resolvedUnit() ?: PelletUnit.BAGS)
    }
    var amountText by remember(editingEntry?.id, lastEntry?.id) {
        mutableStateOf(
            if (initialSourceEntry != null && initialSourceEntry.inputValue > 0.0) {
                val v = initialSourceEntry.inputValue
                if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
            } else {
                "2"
            }
        )
    }
    var noteText by remember(editingEntry) {
        mutableStateOf(editingEntry?.note.orEmpty())
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val parsedAmount = amountText.replace(',', '.').toDoubleOrNull()
    val effectiveBagCap = editingEntry?.bagCapacityKgAtEntry?.takeIf { selectedUnit == PelletUnit.BAGS && editingEntry != null }
        ?: bagCapacityKg

    val resolvedKg = when {
        parsedAmount == null || parsedAmount <= 0.0 -> null
        selectedUnit == PelletUnit.BAGS -> parsedAmount * effectiveBagCap
        else -> parsedAmount
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isMutating) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (editingEntry == null) {
                    stringResource(R.string.entry_sheet_add_title)
                } else {
                    stringResource(R.string.entry_sheet_edit_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Date selector
            Text(
                text = stringResource(R.string.entry_date_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("entry_date_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = CalendarDay.fromEpochDay(selectedEpochDay).toDisplayString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Unit segmented selector
            Text(
                text = stringResource(R.string.entry_unit_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedUnit == PelletUnit.BAGS,
                    onClick = {
                        if (selectedUnit != PelletUnit.BAGS) {
                            selectedUnit = PelletUnit.BAGS
                            validationError = null
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("unit_bags_button")
                ) {
                    Text(stringResource(R.string.entry_unit_bags))
                }
                SegmentedButton(
                    selected = selectedUnit == PelletUnit.KG,
                    onClick = {
                        if (selectedUnit != PelletUnit.KG) {
                            selectedUnit = PelletUnit.KG
                            validationError = null
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("unit_kg_button")
                ) {
                    Text(stringResource(R.string.entry_unit_kg))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount input
            OutlinedTextField(
                value = amountText,
                onValueChange = { next ->
                    if (next.length <= 10) {
                        amountText = next
                        validationError = null
                    }
                },
                label = {
                    Text(
                        text = if (selectedUnit == PelletUnit.BAGS) {
                            stringResource(R.string.entry_amount_bags_label)
                        } else {
                            stringResource(R.string.entry_amount_kg_label)
                        }
                    )
                },
                suffix = {
                    Text(if (selectedUnit == PelletUnit.BAGS) "worki" else "kg")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = validationError != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_amount_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick amount chips (1..9 bags with selected state highlighting last/active amount)
            val quickValues = if (selectedUnit == PelletUnit.BAGS) {
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
            } else {
                listOf("15", "30", "45", "60", "75", "90", "105", "120", "135")
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickValues.forEach { preset ->
                    val presetValue = preset.toDoubleOrNull()
                    val isSelected = parsedAmount != null &&
                        presetValue != null &&
                        kotlin.math.abs(parsedAmount - presetValue) < 0.001
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            amountText = preset
                            validationError = null
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
                        label = {
                            Text(
                                if (selectedUnit == PelletUnit.BAGS) "$preset w." else "$preset kg"
                            )
                        },
                        modifier = Modifier.testTag("quick_amount_chip_${selectedUnit.firestoreValue}_$preset")
                    )
                }
            }

            // Live conversion preview banner
            if (resolvedKg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val conversionText = if (selectedUnit == PelletUnit.BAGS) {
                        stringResource(
                            R.string.entry_conversion_bags_to_kg,
                            formatKg(resolvedKg),
                            formatKg(effectiveBagCap)
                        )
                    } else {
                        val eqBags = if (bagCapacityKg > 0) resolvedKg / bagCapacityKg else 0.0
                        stringResource(
                            R.string.entry_conversion_kg_to_bags,
                            formatNumber(eqBags),
                            formatKg(bagCapacityKg)
                        )
                    }
                    Text(
                        text = conversionText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 120) noteText = it },
                label = { Text(stringResource(R.string.entry_note_label)) },
                placeholder = { Text(stringResource(R.string.entry_note_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_note_input")
            )

            if (validationError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            val invalidAmountMsg = stringResource(R.string.entry_error_invalid_amount)
            Button(
                onClick = {
                    val value = amountText.replace(',', '.').toDoubleOrNull()
                    val totalKg = when {
                        value == null -> 0.0
                        selectedUnit == PelletUnit.BAGS -> value * effectiveBagCap
                        else -> value
                    }
                    if (value == null || value <= 0.0 || value > 10000.0 || totalKg <= 0.0 || totalKg > 10000.0) {
                        validationError = invalidAmountMsg
                    } else {
                        onSave(
                            selectedEpochDay,
                            selectedUnit,
                            value,
                            noteText.trim().takeIf { it.isNotEmpty() }
                        )
                    }
                },
                enabled = !isMutating,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_entry_button")
            ) {
                if (isMutating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = if (editingEntry == null) {
                        stringResource(R.string.entry_save_button)
                    } else {
                        stringResource(R.string.entry_update_button)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CalendarDay.fromEpochDay(selectedEpochDay).toUtcMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedEpochDay = CalendarDay.fromUtcMillis(millis).toEpochDay()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Wybierz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
