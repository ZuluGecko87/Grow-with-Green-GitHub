package com.z.grow.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.z.grow.R

private enum class Tab(val route: String, val label: String) {
    CALENDAR("calendar", "Calendar"),
    PLANTS("plants", "Plants"),
    JOURNAL("journal", "Journal"),
    ENVIRONMENTS("environments", "Spaces"),
}

@Composable
fun GrowApp(vm: GrowViewModel = viewModel()) {
    val nav = rememberNavController()
    val data by vm.data.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                when (tab) {
                                    Tab.CALENDAR -> Icon(Icons.Filled.DateRange, null)
                                    Tab.PLANTS -> Icon(painterResource(R.drawable.ic_sprout), null)
                                    Tab.JOURNAL -> Icon(Icons.Filled.Edit, null)
                                    Tab.ENVIRONMENTS -> Icon(Icons.Filled.Home, null)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.CALENDAR.route, modifier = Modifier.padding(padding)) {
            composable(Tab.CALENDAR.route) {
                CalendarScreen(
                    data = data,
                    vm = vm,
                    onAddEnvironment = { nav.navigate("environment/0") },
                    onAddPlant = { nav.navigate("plant/0") },
                    onOpenJournal = { nav.navigate("journal/$it") },
                    onNewJournal = { day -> nav.navigate("journal/0?day=$day") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable(Tab.PLANTS.route) {
                PlantsScreen(data, onOpen = { nav.navigate("plant/$it") })
            }
            composable(Tab.JOURNAL.route) {
                JournalScreen(data, onOpen = { nav.navigate("journal/$it") }, onNew = { nav.navigate("journal/0") })
            }
            composable(Tab.ENVIRONMENTS.route) {
                EnvironmentsScreen(data, onOpen = { nav.navigate("environment/$it") })
            }
            composable("plant/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                val id = it.arguments?.getLong("id") ?: 0L
                PlantEditScreen(
                    id = id,
                    data = data,
                    vm = vm,
                    onDone = { nav.popBackStack() },
                    onNewJournal = { nav.navigate("journal/0?plant=$id") },
                )
            }
            composable("environment/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EnvironmentEditScreen(it.arguments?.getLong("id") ?: 0L, vm, onDone = { nav.popBackStack() })
            }
            composable(
                "journal/{id}?day={day}&plant={plant}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("day") {
                        type = NavType.LongType
                        defaultValue = Long.MIN_VALUE
                    },
                    navArgument("plant") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) {
                val args = requireNotNull(it.arguments)
                JournalEditScreen(
                    id = args.getLong("id"),
                    initialDay = args.getLong("day").takeIf { day -> day != Long.MIN_VALUE },
                    initialPlantId = args.getLong("plant").takeIf { plant -> plant != 0L },
                    data = data,
                    vm = vm,
                    onDone = { nav.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
