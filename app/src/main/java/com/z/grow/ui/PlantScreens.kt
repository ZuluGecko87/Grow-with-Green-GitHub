package com.z.grow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.z.grow.data.Genetics
import com.z.grow.data.GrowData
import com.z.grow.data.GrowEnvironment
import com.z.grow.data.GrowStage
import com.z.grow.data.Plant
import com.z.grow.data.PlantSource
import com.z.grow.data.PlantType
import com.z.grow.data.TaskKind
import com.z.grow.data.nextDay
import java.time.LocalDate

@Composable
fun PlantsScreen(data: GrowData, onOpen: (Long) -> Unit) {
    Scaffold(
        topBar = { GrowTopBar("Plants") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(0) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add plant") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (data.plants.isEmpty()) {
            EmptyState("No plants yet.\nAdd one with its strain, genetics and schedule.", Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(data.plants, key = { it.id }) { plant ->
                    PlantCard(plant, data.environment(plant.environmentId)) { onOpen(plant.id) }
                }
            }
        }
    }
}

@Composable
private fun PlantCard(plant: Plant, environment: GrowEnvironment?, onClick: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    val day = today - plant.startEpochDay + 1
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(plant.displayName, style = MaterialTheme.typography.titleMedium)
            val lineage = listOf(
                plant.strain.takeIf { it.isNotBlank() && it != plant.displayName },
                plant.manufacturer.takeIf { it.isNotBlank() },
            ).filterNotNull().joinToString(" · ")
            if (lineage.isNotEmpty()) Text(lineage, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${plant.type.label} · ${plant.genetics.label} · ${plant.source.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dayLabel = if (day >= 1) "Day $day" else "Starts ${formatDay(plant.startEpochDay)}"
            Text(
                "${plant.stage.label} · $dayLabel · ${environment?.name ?: "No environment"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (plant.stage != GrowStage.HARVESTED) {
                val nextWater = plant.nextDay(TaskKind.WATER, today)
                val nextFeed = plant.nextDay(TaskKind.FEED, today)
                val upcoming = listOfNotNull(
                    nextWater?.let { "Water ${formatDay(it)}" },
                    nextFeed?.let { "Feed ${formatDay(it)}" },
                ).joinToString(" · ")
                if (upcoming.isNotEmpty()) Text("Next: $upcoming", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PlantEditScreen(id: Long, data: GrowData, vm: GrowViewModel, onDone: () -> Unit) {
    var form by remember { mutableStateOf(if (id == 0L) Plant() else null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) form = vm.plant(id) ?: Plant()
    }

    Scaffold(
        topBar = {
            GrowTopBar(if (id == 0L) "New plant" else "Edit plant", onBack = onDone) {
                if (id != 0L) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete plant") }
                }
            }
        },
    ) { padding ->
        val f = form
        if (f == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Plant")
            OutlinedTextField(
                value = f.name,
                onValueChange = { form = f.copy(name = it) },
                label = { Text("Name / label") },
                placeholder = { Text("e.g. Tent A #1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField("Plant type", PlantType.entries, f.type, { it.label }) { form = f.copy(type = it) }

            SectionTitle("Genetics")
            OutlinedTextField(
                value = f.strain,
                onValueChange = { form = f.copy(strain = it) },
                label = { Text("Strain / variety") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField("Genetics", Genetics.entries, f.genetics, { it.label }) { form = f.copy(genetics = it) }
            DropdownField("Started from", PlantSource.entries, f.source, { it.label }) { form = f.copy(source = it) }
            OutlinedTextField(
                value = f.manufacturer,
                onValueChange = { form = f.copy(manufacturer = it) },
                label = { Text("Manufacturer / breeder") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Grow")
            DropdownField(
                label = "Environment",
                options = listOf<GrowEnvironment?>(null) + data.environments,
                selected = data.environment(f.environmentId),
                optionLabel = { it?.let { env -> "${env.name} (${env.type.label})" } ?: "None" },
            ) { form = f.copy(environmentId = it?.id) }
            DropdownField("Stage", GrowStage.entries, f.stage, { it.label }) { form = f.copy(stage = it) }
            DateField("Start date", f.startEpochDay) { form = f.copy(startEpochDay = it) }

            SectionTitle("Schedule")
            DayStepper("Water", f.waterEveryDays, { form = f.copy(waterEveryDays = it) })
            DayStepper("Feed nutrients", f.feedEveryDays, { form = f.copy(feedEveryDays = it) })
            Text(
                "Schedules count from the start date. On feed days the plant is watered with the environment's nutrient mix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = f.notes,
                onValueChange = { form = f.copy(notes = it) },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.savePlant(f)
                    onDone()
                },
                enabled = f.name.isNotBlank() || f.strain.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save plant") }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "this plant",
            onConfirm = {
                form?.let { vm.deletePlant(it) }
                confirmDelete = false
                onDone()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
