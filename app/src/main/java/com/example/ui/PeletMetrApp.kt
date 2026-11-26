package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.PelletRepository
import com.example.model.CalendarDay
import com.example.model.PelletEntry
import com.example.ui.auth.signOut
import com.example.ui.components.EntryEditorSheet
import com.example.ui.components.formatKg
import com.example.ui.screens.ChartsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.PelletDashboardData
import com.example.viewmodel.PelletViewModel
import com.example.viewmodel.UiState
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_CHARTS = "charts"
const val ROUTE_HISTORY = "history"
const val ROUTE_SETTINGS = "settings"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val destinations = listOf(
    NavDestination(
        route = ROUTE_CHARTS,
        labelRes = R.string.nav_charts,
        selectedIcon = Icons.Rounded.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
        testTag = "nav_charts"
    ),
    NavDestination(
        route = ROUTE_HISTORY,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    NavDestination(
        route = ROUTE_SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

@Composable
fun PeletMetrApp(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: PelletViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PelletViewModel(PelletRepository(db), currentUserId)
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    var showEntrySheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PelletEntry?>(null) }
    var entryPendingDelete by remember { mutableStateOf<PelletEntry?>(null) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_CHARTS

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (currentRoute != ROUTE_SETTINGS && uiState is UiState.Success) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingEntry = null
                            showEntrySheet = true
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null
                            )
                        },
                        text = { Text(stringResource(R.string.fab_add_entry)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_entry_fab")
                    )
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = uiState) {
                        UiState.Loading -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { navigateTo(ROUTE_CHARTS) }) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }

                        is UiState.Success<PelletDashboardData> -> {
                            val data = state.data
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_CHARTS,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ROUTE_CHARTS) {
                                    ChartsScreen(
                                        data = data,
                                        onSelectYear = viewModel::selectYear,
                                        onSelectMonth = viewModel::selectMonth,
                                        onSelectMetricMode = viewModel::selectChartMetricMode,
                                        onAddFirstEntry = {
                                            editingEntry = null
                                            showEntrySheet = true
                                        },
                                        onEditEntry = { entry ->
                                            editingEntry = entry
                                            showEntrySheet = true
                                        },
                                        onNavigateToHistory = { navigateTo(ROUTE_HISTORY) },
                                        onNavigateToSettings = { navigateTo(ROUTE_SETTINGS) }
                                    )
                                }

                                composable(ROUTE_HISTORY) {
                                    HistoryScreen(
                                        entries = data.entries,
                                        onEditEntry = { entry ->
                                            editingEntry = entry
                                            showEntrySheet = true
                                        },
                                        onRequestDeleteEntry = { entry ->
                                            entryPendingDelete = entry
                                        }
                                    )
                                }

                                composable(ROUTE_SETTINGS) {
                                    SettingsScreen(
                                        settings = data.settings,
                                        stockBatches = data.stockBatches,
                                        stockSummary = data.summary.stockSummary,
                                        updateCheckState = updateCheckState,
                                        userEmail = userEmail,
                                        userDisplayName = userDisplayName,
                                        isMutating = isMutating,
                                        onSaveSettings = viewModel::saveSettings,
                                        onAddStockBatch = { bags, bagCap, pricePerBag, purchaseDateEpochDay, purchasePlace, pelletType, note ->
                                            viewModel.addStockBatch(
                                                bagsCount = bags,
                                                bagCapacityKg = bagCap,
                                                pricePerBagPln = pricePerBag,
                                                purchaseDateEpochDay = purchaseDateEpochDay,
                                                purchasePlace = purchasePlace,
                                                pelletType = pelletType,
                                                note = note
                                            )
                                        },
                                        onDeleteStockBatch = viewModel::deleteStockBatch,
                                        onCheckForUpdates = viewModel::checkForUpdates,
                                        onSignOut = {
                                            signOut(
                                                credentialManager = credentialManager,
                                                scope = scope
                                            )
                                        }
                                    )
                                }
                            }

                            if (showEntrySheet) {
                                val lastEntry = remember(data.entries) {
                                    data.entries.maxWithOrNull(
                                        compareBy<PelletEntry>(
                                            { it.createdAt?.seconds ?: Long.MIN_VALUE },
                                            { it.createdAt?.nanoseconds ?: Int.MIN_VALUE },
                                            { it.dateEpochDay }
                                        )
                                    )
                                }
                                EntryEditorSheet(
                                    editingEntry = editingEntry,
                                    lastEntry = lastEntry,
                                    bagCapacityKg = data.settings.bagCapacityKg,
                                    isMutating = isMutating,
                                    onDismiss = {
                                        showEntrySheet = false
                                        editingEntry = null
                                    },
                                    onSave = { epochDay, unit, value, note ->
                                        viewModel.saveEntry(
                                            editingEntryId = editingEntry?.id,
                                            dateEpochDay = epochDay,
                                            inputUnit = unit,
                                            inputValue = value,
                                            bagCapacityKg = data.settings.bagCapacityKg,
                                            note = note,
                                            onSuccess = {
                                                showEntrySheet = false
                                                editingEntry = null
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = entryPendingDelete
    if (pendingDelete != null) {
        val dateText = CalendarDay.fromEpochDay(pendingDelete.dateEpochDay).toDisplayString()
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_message,
                        dateText,
                        formatKg(pendingDelete.weightKg)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(pendingDelete.id)
                        entryPendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
