package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar
import java.util.TimeZone

enum class CalculationMethod(val firestoreValue: String) {
    FULL_REFILL("FULL_REFILL"),
    DIRECT_SUM("DIRECT_SUM");

    companion object {
        fun fromFirestore(raw: String?): CalculationMethod =
            entries.firstOrNull { it.firestoreValue == raw } ?: FULL_REFILL
    }
}

enum class PelletUnit(val firestoreValue: String) {
    BAGS("BAGS"),
    KG("KG");

    companion object {
        fun fromFirestore(raw: String?): PelletUnit =
            entries.firstOrNull { it.firestoreValue == raw } ?: BAGS
    }
}

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bagCapacityKg: Double = 15.0,
    val calculationMethod: String = CalculationMethod.FULL_REFILL.firestoreValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedMethod(): CalculationMethod = CalculationMethod.fromFirestore(calculationMethod)
}

@Keep
@IgnoreExtraProperties
data class PelletEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateEpochDay: Long = 0L,
    val inputUnit: String = PelletUnit.BAGS.firestoreValue,
    val inputValue: Double = 0.0,
    val bagCapacityKgAtEntry: Double = 15.0,
    val weightKg: Double = 0.0,
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedUnit(): PelletUnit = PelletUnit.fromFirestore(inputUnit)
}

@Keep
@IgnoreExtraProperties
data class PelletStockBatch(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bagsCount: Double = 0.0,
    val bagCapacityKg: Double = 15.0,
    val totalKg: Double = 0.0,
    val pricePerBagPln: Double = 0.0,
    val totalCostPln: Double = 0.0,
    val purchaseDateEpochDay: Long? = null,
    val purchasePlace: String? = null,
    val pelletType: String? = null,
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Pure Kotlin calendar day helper compatible with minSdk 24 (no java.time API 26 dependency).
 * Uses Howard Hinnant's civil-from-days / days-from-civil algorithms.
 */
data class CalendarDay(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDay> {

    fun toEpochDay(): Long {
        val y = year.toLong() - (if (month <= 2) 1L else 0L)
        val era = (if (y >= 0L) y else y - 399L) / 400L
        val yoe = y - era * 400L
        val m = month.toLong()
        val doy = (153L * (m + (if (m > 2L) -3L else 9L)) + 2L) / 5L + day.toLong() - 1L
        val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
        return era * 146097L + doe - 719468L
    }

    fun toUtcMillis(): Long = toEpochDay() * 86_400_000L

    fun toDisplayString(): String = "$day ${monthGenitivePl(month)} $year"

    fun toShortDisplayString(): String = "$day ${shortMonthPl(month)} $year"

    fun toMonthYearLabel(): String = "${monthNominativePl(month)} $year"

    override fun compareTo(other: CalendarDay): Int = toEpochDay().compareTo(other.toEpochDay())

    companion object {
        fun fromEpochDay(epochDay: Long): CalendarDay {
            val z = epochDay + 719468L
            val era = (if (z >= 0L) z else z - 146096L) / 146097L
            val doe = z - era * 146097L
            val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
            val y = yoe + era * 400L
            val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
            val mp = (5L * doy + 2L) / 153L
            val d = doy - (153L * mp + 2L) / 5L + 1L
            val m = mp + (if (mp < 10L) 3L else -9L)
            val year = (y + (if (m <= 2L) 1L else 0L)).toInt()
            return CalendarDay(year = year, month = m.toInt(), day = d.toInt())
        }

        fun fromUtcMillis(utcMillis: Long): CalendarDay {
            val epochDay = Math.floorDiv(utcMillis, 86_400_000L)
            return fromEpochDay(epochDay)
        }

        fun today(): CalendarDay {
            val cal = Calendar.getInstance()
            return CalendarDay(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun daysInMonth(year: Int, month: Int): Int {
            return when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> {
                    val isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
                    if (isLeap) 29 else 28
                }
                else -> 30
            }
        }

        fun monthNominativePl(month: Int): String = when (month) {
            1 -> "Styczeń"
            2 -> "Luty"
            3 -> "Marzec"
            4 -> "Kwiecień"
            5 -> "Maj"
            6 -> "Czerwiec"
            7 -> "Lipiec"
            8 -> "Sierpień"
            9 -> "Wrzesień"
            10 -> "Październik"
            11 -> "Listopad"
            12 -> "Grudzień"
            else -> ""
        }

        fun monthGenitivePl(month: Int): String = when (month) {
            1 -> "stycznia"
            2 -> "lutego"
            3 -> "marca"
            4 -> "kwietnia"
            5 -> "maja"
            6 -> "czerwca"
            7 -> "lipca"
            8 -> "sierpnia"
            9 -> "września"
            10 -> "października"
            11 -> "listopada"
            12 -> "grudnia"
            else -> ""
        }

        fun shortMonthPl(month: Int): String = when (month) {
            1 -> "Sty"
            2 -> "Lut"
            3 -> "Mar"
            4 -> "Kwi"
            5 -> "Maj"
            6 -> "Cze"
            7 -> "Lip"
            8 -> "Sie"
            9 -> "Wrz"
            10 -> "Paź"
            11 -> "Lis"
            12 -> "Gru"
            else -> ""
        }
    }
}
