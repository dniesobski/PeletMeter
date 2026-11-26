package com.example.domain

import com.example.model.CalculationMethod
import com.example.model.CalendarDay
import com.example.model.PelletEntry
import com.example.model.PelletStockBatch

data class StockSummary(
    val totalPurchasedKg: Double,
    val totalPurchasedBags: Double,
    val totalCostPln: Double,
    val weightedAvgPricePerKg: Double,
    val weightedAvgPricePerBag: Double,
    val remainingKg: Double,
    val remainingBags: Double,
    val hasPriceData: Boolean
)

data class MonthlyConsumption(
    val year: Int,
    val month: Int,
    val consumedKg: Double,
    val pouredKg: Double,
    val consumedCostPln: Double,
    val activeDays: Int,
    val entryCount: Int
) {
    val averageDailyKg: Double
        get() = if (activeDays > 0) consumedKg / activeDays.toDouble() else 0.0

    val averageDailyCostPln: Double
        get() = if (activeDays > 0) consumedCostPln / activeDays.toDouble() else 0.0
}

data class ConsumptionSummary(
    val selectedYear: Int,
    val monthlyData: List<MonthlyConsumption>,
    val yearTotalConsumedKg: Double,
    val yearTotalPouredKg: Double,
    val yearTotalCostPln: Double,
    val latestEntry: PelletEntry?,
    val isSingleBaselineOnly: Boolean,
    val availableYears: List<Int>,
    val stockSummary: StockSummary
)

object PelletConsumptionCalculator {

    private data class DailyAggregate(
        val epochDay: Long,
        val totalWeightKg: Double
    )

    fun calculateStockSummary(
        stockBatches: List<PelletStockBatch>,
        entries: List<PelletEntry>,
        currentBagCapacityKg: Double
    ): StockSummary {
        val totalPurchasedKg = stockBatches.sumOf { it.totalKg.coerceAtLeast(0.0) }
        val totalPurchasedBags = stockBatches.sumOf { it.bagsCount.coerceAtLeast(0.0) }
        val totalCostPln = stockBatches.sumOf { it.totalCostPln.coerceAtLeast(0.0) }
        val totalPouredKg = entries.sumOf { it.weightKg.coerceAtLeast(0.0) }

        val weightedAvgPricePerKg = if (totalPurchasedKg > 0.0) {
            totalCostPln / totalPurchasedKg
        } else {
            0.0
        }
        val weightedAvgPricePerBag = weightedAvgPricePerKg * currentBagCapacityKg.coerceAtLeast(1.0)
        val remainingKg = (totalPurchasedKg - totalPouredKg).coerceAtLeast(0.0)
        val remainingBags = if (currentBagCapacityKg > 0.0) remainingKg / currentBagCapacityKg else 0.0

        return StockSummary(
            totalPurchasedKg = totalPurchasedKg,
            totalPurchasedBags = totalPurchasedBags,
            totalCostPln = totalCostPln,
            weightedAvgPricePerKg = weightedAvgPricePerKg,
            weightedAvgPricePerBag = weightedAvgPricePerBag,
            remainingKg = remainingKg,
            remainingBags = remainingBags,
            hasPriceData = stockBatches.isNotEmpty() && totalPurchasedKg > 0.0 && totalCostPln > 0.0
        )
    }

    fun calculateForYear(
        entries: List<PelletEntry>,
        method: CalculationMethod,
        selectedYear: Int,
        currentYear: Int = CalendarDay.today().year,
        stockBatches: List<PelletStockBatch> = emptyList(),
        currentBagCapacityKg: Double = 15.0
    ): ConsumptionSummary {
        val stockSummary = calculateStockSummary(
            stockBatches = stockBatches,
            entries = entries,
            currentBagCapacityKg = currentBagCapacityKg
        )
        val avgPricePerKg = stockSummary.weightedAvgPricePerKg

        val consumedByMonth = mutableMapOf<Pair<Int, Int>, Double>()
        val pouredByMonth = mutableMapOf<Pair<Int, Int>, Double>()
        val activeDaysByMonth = mutableMapOf<Pair<Int, Int>, MutableSet<Long>>()
        val entryCountByMonth = mutableMapOf<Pair<Int, Int>, Int>()
        val yearsFound = mutableSetOf(currentYear, selectedYear)

        for (entry in entries) {
            val day = CalendarDay.fromEpochDay(entry.dateEpochDay)
            yearsFound.add(day.year)
            val key = day.year to day.month
            pouredByMonth[key] = (pouredByMonth[key] ?: 0.0) + entry.weightKg
            entryCountByMonth[key] = (entryCountByMonth[key] ?: 0) + 1
        }

        val dailyAggregates = entries
            .groupBy { it.dateEpochDay }
            .entries
            .sortedBy { it.key }
            .map { (epochDay, dayEntries) ->
                DailyAggregate(
                    epochDay = epochDay,
                    totalWeightKg = dayEntries.sumOf { it.weightKg }
                )
            }

        val isSingleBaselineOnly: Boolean
        when (method) {
            CalculationMethod.FULL_REFILL -> {
                isSingleBaselineOnly = dailyAggregates.size == 1
                for (i in 1 until dailyAggregates.size) {
                    val prev = dailyAggregates[i - 1]
                    val curr = dailyAggregates[i]
                    val spanDays = (curr.epochDay - prev.epochDay).coerceAtLeast(1L)
                    val dailyRate = curr.totalWeightKg / spanDays.toDouble()
                    for (offset in 1L..spanDays) {
                        val dayEpoch = prev.epochDay + offset
                        val calDay = CalendarDay.fromEpochDay(dayEpoch)
                        yearsFound.add(calDay.year)
                        val key = calDay.year to calDay.month
                        consumedByMonth[key] = (consumedByMonth[key] ?: 0.0) + dailyRate
                        activeDaysByMonth.getOrPut(key) { mutableSetOf() }.add(dayEpoch)
                    }
                }
            }

            CalculationMethod.DIRECT_SUM -> {
                isSingleBaselineOnly = false
                for (entry in entries) {
                    val day = CalendarDay.fromEpochDay(entry.dateEpochDay)
                    val key = day.year to day.month
                    consumedByMonth[key] = (consumedByMonth[key] ?: 0.0) + entry.weightKg
                    activeDaysByMonth.getOrPut(key) { mutableSetOf() }.add(entry.dateEpochDay)
                }
            }
        }

        val monthlyData = (1..12).map { month ->
            val key = selectedYear to month
            val consumed = consumedByMonth[key] ?: 0.0
            val poured = pouredByMonth[key] ?: 0.0
            val activeDays = when (method) {
                CalculationMethod.FULL_REFILL -> activeDaysByMonth[key]?.size ?: 0
                CalculationMethod.DIRECT_SUM -> {
                    if (consumed > 0.0) CalendarDay.daysInMonth(selectedYear, month) else 0
                }
            }
            MonthlyConsumption(
                year = selectedYear,
                month = month,
                consumedKg = consumed,
                pouredKg = poured,
                consumedCostPln = consumed * avgPricePerKg,
                activeDays = activeDays,
                entryCount = entryCountByMonth[key] ?: 0
            )
        }

        val latestEntry = entries.maxWithOrNull(
            compareBy<PelletEntry> { it.dateEpochDay }
                .thenBy { it.createdAt?.seconds ?: 0L }
        )

        return ConsumptionSummary(
            selectedYear = selectedYear,
            monthlyData = monthlyData,
            yearTotalConsumedKg = monthlyData.sumOf { it.consumedKg },
            yearTotalPouredKg = monthlyData.sumOf { it.pouredKg },
            yearTotalCostPln = monthlyData.sumOf { it.consumedCostPln },
            latestEntry = latestEntry,
            isSingleBaselineOnly = isSingleBaselineOnly,
            availableYears = yearsFound.sorted(),
            stockSummary = stockSummary
        )
    }
}
