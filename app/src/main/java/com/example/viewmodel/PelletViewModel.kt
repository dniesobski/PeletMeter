package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubUpdateChecker
import com.example.data.PelletRepository
import com.example.data.UpdateCheckState
import com.example.domain.ConsumptionSummary
import com.example.domain.PelletConsumptionCalculator
import com.example.model.CalculationMethod
import com.example.model.CalendarDay
import com.example.model.PelletEntry
import com.example.model.PelletStockBatch
import com.example.model.PelletUnit
import com.example.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ChartMetricMode {
    WEIGHT_KG,
    COST_PLN
}

data class PelletDashboardData(
    val settings: UserSettings,
    val entries: List<PelletEntry>,
    val stockBatches: List<PelletStockBatch>,
    val summary: ConsumptionSummary,
    val selectedMonth: Int,
    val chartMetricMode: ChartMetricMode
)

class PelletViewModel(
    private val repository: PelletRepository,
    private val currentUserId: String
) : ViewModel() {

    private val today = CalendarDay.today()
    private val _selectedYear = MutableStateFlow(today.year)
    private val _selectedMonth = MutableStateFlow(today.month)
    private val _chartMetricMode = MutableStateFlow(ChartMetricMode.WEIGHT_KG)
    private val _isMutating = MutableStateFlow(false)
    private val _actionMessage = MutableStateFlow<String?>(null)
    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)

    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val selectionFlow = combine(
        _selectedYear,
        _selectedMonth,
        _chartMetricMode
    ) { year, month, mode ->
        Triple(year, month, mode)
    }

    val uiState: StateFlow<UiState<PelletDashboardData>> = combine(
        repository.observeUserSettings(currentUserId),
        repository.observeEntries(currentUserId),
        repository.observeStockBatches(currentUserId),
        selectionFlow
    ) { settings, entries, stockBatches, (year, month, mode) ->
        val summary = PelletConsumptionCalculator.calculateForYear(
            entries = entries,
            method = settings.resolvedMethod(),
            selectedYear = year,
            currentYear = today.year,
            stockBatches = stockBatches,
            currentBagCapacityKg = settings.bagCapacityKg
        )
        UiState.Success(
            PelletDashboardData(
                settings = settings,
                entries = entries,
                stockBatches = stockBatches,
                summary = summary,
                selectedMonth = month,
                chartMetricMode = mode
            )
        ) as UiState<PelletDashboardData>
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Błąd obserwowania danych peletu", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Nie udało się pobrać danych z chmury."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    fun selectMonth(month: Int) {
        if (month in 1..12) {
            _selectedMonth.value = month
        }
    }

    fun selectChartMetricMode(mode: ChartMetricMode) {
        _chartMetricMode.value = mode
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun saveEntry(
        editingEntryId: String?,
        dateEpochDay: Long,
        inputUnit: PelletUnit,
        inputValue: Double,
        bagCapacityKg: Double,
        note: String?,
        onSuccess: () -> Unit
    ) {
        val resolvedWeightKg = when (inputUnit) {
            PelletUnit.BAGS -> inputValue * bagCapacityKg
            PelletUnit.KG -> inputValue
        }
        if (inputValue <= 0.0 || resolvedWeightKg <= 0.0 || resolvedWeightKg > 10000.0) {
            _actionMessage.value = "Podaj poprawną ilość większą od zera (maks. 10 000 kg)."
            return
        }

        viewModelScope.launch {
            _isMutating.value = true
            val result = if (editingEntryId.isNullOrBlank()) {
                repository.addEntry(
                    dateEpochDay = dateEpochDay,
                    inputUnit = inputUnit,
                    inputValue = inputValue,
                    bagCapacityKgAtEntry = bagCapacityKg,
                    weightKg = resolvedWeightKg,
                    note = note
                )
            } else {
                repository.updateEntry(
                    entryId = editingEntryId,
                    dateEpochDay = dateEpochDay,
                    inputUnit = inputUnit,
                    inputValue = inputValue,
                    bagCapacityKgAtEntry = bagCapacityKg,
                    weightKg = resolvedWeightKg,
                    note = note
                )
            }
            _isMutating.value = false

            result.fold(
                onSuccess = {
                    val savedDay = CalendarDay.fromEpochDay(dateEpochDay)
                    _selectedYear.value = savedDay.year
                    _selectedMonth.value = savedDay.month
                    _actionMessage.value = if (editingEntryId.isNullOrBlank()) {
                        "Zapisano wsyp peletu."
                    } else {
                        "Zaktualizowano wpis."
                    }
                    onSuccess()
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Nie udało się zapisać wsypu."
                }
            )
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteEntry(entryId)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Usunięto wpis."
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Nie udało się usunąć wpisu."
                }
            )
        }
    }

    fun saveSettings(bagCapacityKg: Double, calculationMethod: CalculationMethod) {
        if (bagCapacityKg !in 1.0..100.0) {
            _actionMessage.value = "Pojemność worka musi mieścić się w zakresie 1–100 kg."
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.saveUserSettings(
                bagCapacityKg = bagCapacityKg,
                calculationMethod = calculationMethod
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Zapisano ustawienia kotła."
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Nie udało się zapisać ustawień."
                }
            )
        }
    }

    fun addStockBatch(
        bagsCount: Double,
        bagCapacityKg: Double,
        pricePerBagPln: Double,
        purchaseDateEpochDay: Long,
        purchasePlace: String?,
        pelletType: String?,
        note: String?,
        onSuccess: () -> Unit = {}
    ) {
        val totalKg = bagsCount * bagCapacityKg
        val totalCostPln = bagsCount * pricePerBagPln
        if (bagsCount <= 0.0 || bagsCount > 10000.0 || totalKg > 100000.0 || pricePerBagPln < 0.0 || pricePerBagPln > 100000.0 || totalCostPln > 10000000.0) {
            _actionMessage.value = "Podaj poprawną liczbę worków i cenę."
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.addStockBatch(
                bagsCount = bagsCount,
                bagCapacityKg = bagCapacityKg,
                pricePerBagPln = pricePerBagPln,
                purchaseDateEpochDay = purchaseDateEpochDay,
                purchasePlace = purchasePlace,
                pelletType = pelletType,
                note = note
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Dodano partię peletu."
                    onSuccess()
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Nie udało się dodać partii peletu."
                }
            )
        }
    }

    fun deleteStockBatch(batchId: String) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteStockBatch(batchId)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Usunięto partię peletu."
                },
                onFailure = { e ->
                    _actionMessage.value = e.localizedMessage ?: "Nie udało się usunąć partii peletu."
                }
            )
        }
    }

    fun checkForUpdates() {
        if (_updateCheckState.value is UpdateCheckState.Checking) return
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            _updateCheckState.value = GitHubUpdateChecker.checkForUpdates()
        }
    }

    private companion object {
        const val TAG = "PelletVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
