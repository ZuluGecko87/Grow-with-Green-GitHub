package com.z.grow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.z.grow.data.Catalog
import com.z.grow.data.Genetics
import com.z.grow.data.GrowData
import com.z.grow.data.GrowEnvironment
import com.z.grow.data.GrowStage
import com.z.grow.data.PhotoStore
import com.z.grow.data.Plant
import com.z.grow.data.PlantSource
import com.z.grow.data.PlantType
import com.z.grow.data.TaskKind
import com.z.grow.data.TaskLog
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
                    PlantCard(
                        plant = plant,
                        environment = data.environment(plant.environmentId),
                        lastPh = data.logsFor(plant.id).firstNotNullOfOrNull { it.ph },
                    ) { onOpen(plant.id) }
                }
            }
        }
    }
}

@Composable
private fun PlantCard(plant: Plant, environment: GrowEnvironment?, lastPh: Double?, onClick: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    val day = today - plant.startEpochDay + 1
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PlantAvatar(plant, size = 72)
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            lastPh?.let { Text("Last pH %.1f".format(it), style = MaterialTheme.typography.bodySmall, color = WaterColor) }
        }
        }
    }
}

@Composable
fun PlantEditScreen(
    id: Long,
    data: GrowData,
    vm: GrowViewModel,
    onDone: () -> Unit,
    onNewJournal: () -> Unit,
    onOpenJournal: (Long) -> Unit,
) {
    val context = LocalContext.current
    var form by remember { mutableStateOf(if (id == 0L) Plant() else null) }
    var original by remember { mutableStateOf<Plant?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<TaskLog?>(null) }
    LaunchedEffect(id) {
        if (id != 0L) {
            val loaded = vm.plant(id) ?: Plant()
            original = loaded
            form = loaded
        }
    }

    /** Deletes a profile photo picked in this session that won't be kept. */
    fun dropUnsavedPhoto(fileName: String?) {
        if (fileName != null && fileName != original?.photoFileName) PhotoStore.delete(context, listOf(fileName))
    }
    val picker = rememberPhotoPicker(maxItems = 1) { names ->
        val current = form ?: return@rememberPhotoPicker
        dropUnsavedPhoto(current.photoFileName)
        form = current.copy(photoFileName = names.first())
    }
    val discard = {
        dropUnsavedPhoto(form?.photoFileName)
        onDone()
    }
    BackHandler(onBack = discard)

    Scaffold(
        topBar = {
            GrowTopBar(if (id == 0L) "New plant" else "Edit plant", onBack = discard) {
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
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlantAvatar(f, size = 132)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(onClick = picker.takePhoto) { Text("Take photo") }
                    OutlinedButton(onClick = picker.choose) { Text("Choose photo") }
                }
                if (id != 0L) {
                    Text(
                        "Each new profile photo is saved to the journal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SectionTitle("Plant")
            OutlinedTextField(
                value = f.name,
                onValueChange = { form = f.copy(name = it) },
                label = { Text("Plant name") },
                placeholder = { Text("e.g. Big Bertha") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField("Plant type", PlantType.entries, f.type, { it.label }) { form = f.copy(type = it) }

            SectionTitle("Genetics")
            SuggestField(
                label = "Manufacturer / breeder",
                value = f.manufacturer,
                suggestions = Catalog.manufacturers(f.type),
                onValueChange = { form = f.copy(manufacturer = it) },
                placeholder = "Pick one or type your own",
            )
            SuggestField(
                label = "Strain / variety",
                value = f.strain,
                suggestions = Catalog.strains(f.type, f.manufacturer),
                onValueChange = { form = f.copy(strain = it) },
                placeholder = "Pick one or type your own",
            )
            DropdownField("Genetics", Genetics.entries, f.genetics, { it.label }) { form = f.copy(genetics = it) }
            DropdownField("Started from", PlantSource.entries, f.source, { it.label }) { form = f.copy(source = it) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reminders for this plant", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Included in the daily reminder (turn it on in Settings).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = f.remindersEnabled, onCheckedChange = { form = f.copy(remindersEnabled = it) })
            }

            OutlinedTextField(
                value = f.notes,
                onValueChange = { form = f.copy(notes = it) },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.savePlant(f, original)
                    onDone()
                },
                enabled = f.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save plant") }

            if (id != 0L) PlantHistory(id, data, onNewJournal, onOpenJournal, onEditLog = { editingLog = it })
        }
    }

    editingLog?.let { LogEditDialog(it, data, vm) { editingLog = null } }

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

@Composable
private fun PlantHistory(
    plantId: Long,
    data: GrowData,
    onNewJournal: () -> Unit,
    onOpenJournal: (Long) -> Unit,
    onEditLog: (TaskLog) -> Unit,
) {
    val timeline = data.timeline(plantId)
    SectionTitle("History")
    val phs = data.logsFor(plantId).mapNotNull { it.ph }.take(5)
    if (phs.isNotEmpty()) {
        Text(
            "Average pH (last ${phs.size}): %.1f".format(phs.average()),
            style = MaterialTheme.typography.bodyMedium,
            color = WaterColor,
        )
    }
    OutlinedButton(onClick = onNewJournal, modifier = Modifier.fillMaxWidth()) { Text("Add journal entry for this plant") }
    if (timeline.isEmpty()) {
        Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    timeline.take(30).forEach { TimelineCard(it, data, onOpenEntry = onOpenJournal, onEditLog = onEditLog) }
    if (timeline.size > 30) {
        Text(
            "Showing the latest 30. See the Journal tab for everything.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
