package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.MonthlyConsumption
import com.example.model.CalendarDay
import com.example.viewmodel.ChartMetricMode
import kotlin.math.ceil
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyChartCard(
    selectedYear: Int,
    selectedMonth: Int,
    monthlyData: List<MonthlyConsumption>,
    bagCapacityKg: Double,
    hasPriceData: Boolean,
    chartMetricMode: ChartMetricMode,
    onSelectMetricMode: (ChartMetricMode) -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode = if (hasPriceData) chartMetricMode else ChartMetricMode.WEIGHT_KG

    val maxBarValue = remember(monthlyData, activeMode) {
        val peak = monthlyData.maxOfOrNull {
            if (activeMode == ChartMetricMode.COST_PLN) it.consumedCostPln else it.consumedKg
        } ?: 0.0
        if (peak <= 0.0) 100.0 else ceil(peak / 50.0) * 50.0
    }

    val selectedStats = monthlyData.firstOrNull { it.month == selectedMonth }

    val primaryColor = if (activeMode == ChartMetricMode.COST_PLN) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val mutedBarColor = primaryColor.copy(alpha = 0.28f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Year selector header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.charts_monthly_heading, selectedYear),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreviousYear,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("chart_prev_year_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Poprzedni rok"
                        )
                    }
                    Text(
                        text = selectedYear.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onNextYear,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("chart_next_year_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Następny rok"
                        )
                    }
                }
            }

            // Metric switcher (kg vs zł) when price data is available
            if (hasPriceData) {
                Spacer(modifier = Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = activeMode == ChartMetricMode.WEIGHT_KG,
                        onClick = { onSelectMetricMode(ChartMetricMode.WEIGHT_KG) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("chart_mode_kg")
                    ) {
                        Text(stringResource(R.string.charts_mode_kg))
                    }
                    SegmentedButton(
                        selected = activeMode == ChartMetricMode.COST_PLN,
                        onClick = { onSelectMetricMode(ChartMetricMode.COST_PLN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("chart_mode_cost")
                    ) {
                        Text(stringResource(R.string.charts_mode_cost))
                    }
                }
            }

            // Selected month spotlight strip
            if (selectedStats != null) {
                val bagsEq = if (bagCapacityKg > 0) selectedStats.consumedKg / bagCapacityKg else 0.0
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${CalendarDay.monthNominativePl(selectedStats.month)} $selectedYear",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.charts_kpi_poured_raw,
                                    formatKg(selectedStats.pouredKg)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (activeMode == ChartMetricMode.COST_PLN) {
                                Text(
                                    text = formatPln(selectedStats.consumedCostPln),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${formatKg(selectedStats.consumedKg)} kg • ${formatNumber(bagsEq)} work.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "${formatKg(selectedStats.consumedKg)} kg",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val subLabel = if (hasPriceData) {
                                    "${formatPln(selectedStats.consumedCostPln)} • ${formatNumber(bagsEq)} work."
                                } else {
                                    stringResource(R.string.charts_kpi_bags_eq, formatNumber(bagsEq))
                                }
                                Text(
                                    text = subLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart area with Y-axis reference labels & interactive 12-month bar columns
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val unitSuffix = if (activeMode == ChartMetricMode.COST_PLN) "zł" else "kg"
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(bottom = 26.dp, end = 8.dp)
                        .width(48.dp)
                ) {
                    Text(
                        text = formatKg(maxBarValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = formatKg(maxBarValue * 0.5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "0 $unitSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Plot area with horizontal gridlines & 12 animated bars
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(bottom = 26.dp)
                    ) {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        val levels = listOf(0f, 0.5f, 1f)
                        levels.forEach { ratio ->
                            val y = size.height * (1f - ratio)
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = if (ratio > 0f) dashEffect else null
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight()
                    ) {
                        monthlyData.forEach { monthStats ->
                            val isSelected = monthStats.month == selectedMonth
                            val barValue = if (activeMode == ChartMetricMode.COST_PLN) {
                                monthStats.consumedCostPln
                            } else {
                                monthStats.consumedKg
                            }
                            val targetRatio = (barValue / max(1.0, maxBarValue))
                                .toFloat()
                                .coerceIn(0f, 1f)
                            val animatedRatio by animateFloatAsState(
                                targetValue = targetRatio,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "bar_height_${monthStats.month}"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onSelectMonth(monthStats.month) }
                                    )
                                    .testTag("chart_month_${monthStats.month}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.BottomCenter,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 3.dp)
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                    ) {
                                        val barWidth = size.width * 0.72f
                                        val left = (size.width - barWidth) / 2f
                                        val minVisibleHeight = if (barValue > 0.0) 6.dp.toPx() else 2.dp.toPx()
                                        val barHeight = max(size.height * animatedRatio, minVisibleHeight)
                                        val top = size.height - barHeight
                                        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                                        if (barValue > 0.0) {
                                            if (isSelected) {
                                                drawRoundRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(primaryColor, secondaryColor)
                                                    ),
                                                    topLeft = Offset(left, top),
                                                    size = Size(barWidth, barHeight),
                                                    cornerRadius = corner
                                                )
                                            } else {
                                                drawRoundRect(
                                                    color = mutedBarColor,
                                                    topLeft = Offset(left, top),
                                                    size = Size(barWidth, barHeight),
                                                    cornerRadius = corner
                                                )
                                            }
                                        } else {
                                            drawRoundRect(
                                                color = gridColor.copy(alpha = if (isSelected) 0.9f else 0.45f),
                                                topLeft = Offset(left, size.height - 3.dp.toPx()),
                                                size = Size(barWidth, 3.dp.toPx()),
                                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = CalendarDay.shortMonthPl(monthStats.month),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
