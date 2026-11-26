package com.example

import com.example.domain.PelletConsumptionCalculator
import com.example.model.CalculationMethod
import com.example.model.CalendarDay
import com.example.model.PelletEntry
import com.example.model.PelletUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PelletCalculatorTest {

    @Test
    fun calendarDay_roundTripEpochDay() {
        val day = CalendarDay(2026, 11, 25)
        val roundTrip = CalendarDay.fromEpochDay(day.toEpochDay())
        assertEquals(day, roundTrip)
    }

    @Test
    fun fullRefill_singleBaselineEntry_marksSingleBaselineOnly() {
        val day1 = CalendarDay(2026, 11, 1).toEpochDay()
        val entries = listOf(
            PelletEntry(
                id = "1",
                dateEpochDay = day1,
                inputUnit = PelletUnit.BAGS.firestoreValue,
                inputValue = 4.0,
                bagCapacityKgAtEntry = 15.0,
                weightKg = 60.0
            )
        )
        val summary = PelletConsumptionCalculator.calculateForYear(
            entries = entries,
            method = CalculationMethod.FULL_REFILL,
            selectedYear = 2026,
            currentYear = 2026
        )
        assertTrue(summary.isSingleBaselineOnly)
        assertEquals(0.0, summary.yearTotalConsumedKg, 0.01)
        assertEquals(60.0, summary.yearTotalPouredKg, 0.01)
    }

    @Test
    fun fullRefill_crossMonthInterval_proratesAccuratelyAcrossMonths() {
        // Baseline full fill on Nov 28, next full fill on Dec 3 with 50 kg -> 5 days (Nov 29, Nov 30, Dec 1, Dec 2, Dec 3)
        // Expected: Nov = 20 kg (2 days * 10 kg), Dec = 30 kg (3 days * 10 kg)
        val nov28 = CalendarDay(2026, 11, 28).toEpochDay()
        val dec3 = CalendarDay(2026, 12, 3).toEpochDay()
        val entries = listOf(
            PelletEntry(id = "e1", dateEpochDay = nov28, weightKg = 60.0),
            PelletEntry(id = "e2", dateEpochDay = dec3, weightKg = 50.0)
        )
        val summary = PelletConsumptionCalculator.calculateForYear(
            entries = entries,
            method = CalculationMethod.FULL_REFILL,
            selectedYear = 2026,
            currentYear = 2026
        )
        assertFalse(summary.isSingleBaselineOnly)
        val nov = summary.monthlyData.first { it.month == 11 }
        val dec = summary.monthlyData.first { it.month == 12 }
        assertEquals(20.0, nov.consumedKg, 0.01)
        assertEquals(30.0, dec.consumedKg, 0.01)
        assertEquals(60.0, nov.pouredKg, 0.01)
        assertEquals(50.0, dec.pouredKg, 0.01)
    }

    @Test
    fun directSum_attributesEachEntryToItsMonth() {
        val nov28 = CalendarDay(2026, 11, 28).toEpochDay()
        val dec3 = CalendarDay(2026, 12, 3).toEpochDay()
        val entries = listOf(
            PelletEntry(id = "e1", dateEpochDay = nov28, weightKg = 60.0),
            PelletEntry(id = "e2", dateEpochDay = dec3, weightKg = 45.0)
        )
        val summary = PelletConsumptionCalculator.calculateForYear(
            entries = entries,
            method = CalculationMethod.DIRECT_SUM,
            selectedYear = 2026,
            currentYear = 2026
        )
        assertEquals(60.0, summary.monthlyData.first { it.month == 11 }.consumedKg, 0.01)
        assertEquals(45.0, summary.monthlyData.first { it.month == 12 }.consumedKg, 0.01)
        assertEquals(105.0, summary.yearTotalConsumedKg, 0.01)
    }

    @Test
    fun stockPricing_weightedAverageAndMonthlyCost_calculatedCorrectly() {
        // Batch 1: 60 bags * 15 kg = 900 kg @ 20 PLN/bag = 1200 PLN
        // Batch 2: 40 bags * 15 kg = 600 kg @ 25 PLN/bag = 1000 PLN
        // Total = 1500 kg, 2200 PLN -> avg = 2200/1500 = 1.46666... PLN/kg (22.00 PLN/bag)
        val batches = listOf(
            com.example.model.PelletStockBatch(
                id = "b1",
                bagsCount = 60.0,
                bagCapacityKg = 15.0,
                totalKg = 900.0,
                pricePerBagPln = 20.0,
                totalCostPln = 1200.0
            ),
            com.example.model.PelletStockBatch(
                id = "b2",
                bagsCount = 40.0,
                bagCapacityKg = 15.0,
                totalKg = 600.0,
                pricePerBagPln = 25.0,
                totalCostPln = 1000.0
            )
        )
        val nov1 = CalendarDay(2026, 11, 1).toEpochDay()
        val nov11 = CalendarDay(2026, 11, 11).toEpochDay()
        val entries = listOf(
            PelletEntry(id = "e1", dateEpochDay = nov1, weightKg = 150.0),
            PelletEntry(id = "e2", dateEpochDay = nov11, weightKg = 300.0)
        )
        val summary = PelletConsumptionCalculator.calculateForYear(
            entries = entries,
            method = CalculationMethod.FULL_REFILL,
            selectedYear = 2026,
            currentYear = 2026,
            stockBatches = batches,
            currentBagCapacityKg = 15.0
        )
        assertTrue(summary.stockSummary.hasPriceData)
        assertEquals(2200.0 / 1500.0, summary.stockSummary.weightedAvgPricePerKg, 0.0001)
        assertEquals(22.0, summary.stockSummary.weightedAvgPricePerBag, 0.001)
        assertEquals(1050.0, summary.stockSummary.remainingKg, 0.01)
        // Consumed in Nov = 300 kg -> cost = 300 * (2200/1500) = 440 PLN
        assertEquals(440.0, summary.monthlyData.first { it.month == 11 }.consumedCostPln, 0.01)
        assertEquals(440.0, summary.yearTotalCostPln, 0.01)
    }
}
