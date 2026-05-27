package com.trigeo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trigeo.app.TrigeoApp
import com.trigeo.app.ui.outings.OutingsListScreen
import com.trigeo.app.ui.outings.OutingsViewModel
import com.trigeo.app.ui.map.OutingMapScreen
import com.trigeo.app.ui.map.OutingMapViewModel
import com.trigeo.app.ui.settings.SettingsScreen
import com.trigeo.app.ui.settings.SettingsViewModel
import java.util.UUID

@Composable
fun TrigeoRoot() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as TrigeoApp
    NavHost(navController = nav, startDestination = "outings") {
        composable("outings") {
            val vm: OutingsViewModel = viewModel(
                factory = OutingsViewModel.factory(app.outingsRepository, app.readingsRepository),
            )
            OutingsListScreen(
                viewModel = vm,
                onOpen = { outing -> nav.navigate("outing/${outing.id}") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("outing/{outingId}") { backStack ->
            val raw = backStack.arguments?.getString("outingId").orEmpty()
            val outingId = runCatching { UUID.fromString(raw) }.getOrNull()
            if (outingId == null) {
                nav.popBackStack()
                return@composable
            }
            val vm: OutingMapViewModel = viewModel(
                factory = OutingMapViewModel.factory(
                    outingsRepo = app.outingsRepository,
                    readingsRepo = app.readingsRepository,
                    settingsRepo = app.settingsRepository,
                    locationService = app.locationService,
                    compassService = app.compassService,
                    outingId = outingId,
                ),
            )
            OutingMapScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app.settingsRepository))
            SettingsScreen(viewModel = vm, onBack = { nav.popBackStack() })
        }
    }
}
