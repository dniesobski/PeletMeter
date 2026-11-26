package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.CalendarDay
import com.example.model.PelletEntry
import com.example.ui.components.PelletEntryCard

@Composable
fun HistoryScreen(
    entries: List<PelletEntry>,
    onEditEntry: (PelletEntry) -> Unit,
    onRequestDeleteEntry: (PelletEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    // Distinct (year, month) pairs present in history, sorted newest first
    val availableMonths = remember(entries) {
        entries
            .map {
                val d = CalendarDay.fromEpochDay(it.dateEpochDay)
                d.year to d.month
            }
            .distinct()
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }

    var selectedMonthFilter by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val filteredEntries = remember(entries, selectedMonthFilter) {
        if (selectedMonthFilter == null) {
            entries
        } else {
            entries.filter {
                val d = CalendarDay.fromEpochDay(it.dateEpochDay)
                (d.year to d.month) == selectedMonthFilter
            }
        }
    }

    val groupedEntries = remember(filteredEntries) {
        filteredEntries.groupBy { entry ->
            val d = CalendarDay.fromEpochDay(entry.dateEpochDay)
            d.year to d.month
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (availableMonths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedMonthFilter == null,
                                onClick = { selectedMonthFilter = null },
                                label = { Text(stringResource(R.string.history_filter_all)) },
                                modifier = Modifier.testTag("history_filter_all")
                            )
                        }
                        items(availableMonths) { (year, month) ->
                            val isSelected = selectedMonthFilter == (year to month)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMonthFilter = if (isSelected) null else (year to month)
                                },
                                label = {
                                    Text("${CalendarDay.shortMonthPl(month)} $year")
                                }
                            )
                        }
                    }
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp).fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.history_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            groupedEntries.forEach { (yearMonth, monthEntries) ->
                item(key = "header_${yearMonth.first}_${yearMonth.second}") {
                    Text(
                        text = "${CalendarDay.monthNominativePl(yearMonth.second)} ${yearMonth.first}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }

                items(monthEntries, key = { it.id }) { entry ->
                    PelletEntryCard(
                        entry = entry,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { onRequestDeleteEntry(entry) },
                        modifier = Modifier.widthIn(max = 720.dp)
                    )
                }
            }
        }
    }
}
