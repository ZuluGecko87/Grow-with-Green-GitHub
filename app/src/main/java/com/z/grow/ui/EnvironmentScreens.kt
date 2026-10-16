package com.z.grow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.z.grow.data.EnvironmentType
import com.z.grow.data.GrowData
import com.z.grow.data.GrowEnvironment
import com.z.grow.data.GrowMedium
import com.z.grow.data.Nutrient
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun EnvironmentsScreen(data: GrowData, onOpen: (Long) -> Unit) {
    Scaffold(
        topBar = { GrowTopBar("Environments") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpen(0) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add environment") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (data.environments.isEmpty()) {
            EmptyState("No environments yet.\nAdd a tent, room, greenhouse or outdoor plot.", Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(data.environments, key = { it.id }) { env ->
                    EnvironmentCard(env, data) { onOpen(env.id) }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentCard(env: GrowEnvironment, data: GrowData, onClick: () -> Unit) {
    val today = LocalDate.now().toEpochDay()
    val plantCount = data.plantsIn(env.id).size
    val nutrients = data.nutrientsFor(env.id)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(env.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(env.type.label, env.medium.label, env.size).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (env.hasLightSchedule) {
                val hours = env.lightHoursOn(today)
                Text(
                    "💡 ${cycleLabel(hours)} · on at ${formatMinute(env.lightsOnMinute)}" +
                        (env.lightType.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                val flip = env.flipEpochDay
                if (flip != null && flip >= today) {
                    Text(
                        "Flips to ${cycleLabel(env.flowerLightHours)} on ${formatDay(flip)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightColor,
                    )
                }
            }
            Text(
                "$plantCount plant${if (plantCount == 1) "" else "s"} · ${nutrients.size} nutrient${if (nutrients.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentEditScreen(id: Long, vm: GrowViewModel, onDone: () -> Unit) {
    var form by remember { mutableStateOf(if (id == 0L) GrowEnvironment() else null) }
    val nutrients = remember { mutableStateListOf<Nutrient>() }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id != 0L) {
            nutrients.addAll(vm.nutrientsFor(id))
            form = vm.environment(id) ?: GrowEnvironment()
        }
    }

    Scaffold(
        topBar = {
            GrowTopBar(if (id == 0L) "New environment" else "Edit environment", onBack = onDone) {
                if (id != 0L) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete environment") }
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
            SectionTitle("Space")
            OutlinedTextField(
                value = f.name,
                onValueChange = { form = f.copy(name = it) },
                label = { Text("Name") },
                placeholder = { Text("e.g. 4x4 Tent") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EnvironmentType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = f.type == type,
                        onClick = { form = f.copy(type = type) },
                        shape = SegmentedButtonDefaults.itemShape(index, EnvironmentType.entries.size),
                    ) { Text(type.label) }
                }
            }
            DropdownField("Growing medium", GrowMedium.entries, f.medium, { it.label }) { form = f.copy(medium = it) }
            OutlinedTextField(
                value = f.size,
                onValueChange = { form = f.copy(size = it) },
                label = { Text("Size") },
                placeholder = { Text("e.g. 4' x 4' or 10 gal pots") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (f.hasLightSchedule) {
                SectionTitle("Lights")
                OutlinedTextField(
                    value = f.lightType,
                    onValueChange = { form = f.copy(lightType = it) },
                    label = { Text("Light") },
                    placeholder = { Text("e.g. 480W LED") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeField("Lights on at", f.lightsOnMinute) { form = f.copy(lightsOnMinute = it) }
                HoursSlider("Light cycle", f.vegLightHours) { form = f.copy(vegLightHours = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Plan a light change", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "e.g. flip to 12/12 for flowering",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = f.flipEpochDay != null,
                        onCheckedChange = { on ->
                            form = f.copy(flipEpochDay = if (on) LocalDate.now().plusWeeks(4).toEpochDay() else null)
                        },
                    )
                }
                val flip = f.flipEpochDay
                if (flip != null) {
                    DateField("Change on", flip) { form = f.copy(flipEpochDay = it) }
                    HoursSlider("New light cycle", f.flowerLightHours) { form = f.copy(flowerLightHours = it) }
                }
            }

            SectionTitle("Nutrients")
            Text(
                "The feeding lineup used on feed days for plants in this environment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            nutrients.forEachIndexed { index, nutrient ->
                NutrientEditor(
                    nutrient = nutrient,
                    onChange = { nutrients[index] = it },
                    onRemove = { nutrients.removeAt(index) },
                )
            }
            OutlinedButton(onClick = { nutrients.add(Nutrient()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Text("Add nutrient", modifier = Modifier.padding(start = 8.dp))
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
                    vm.saveEnvironment(f, nutrients.toList())
                    onDone()
                },
                enabled = f.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save environment") }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "this environment",
            onConfirm = {
                form?.let { vm.deleteEnvironment(it) }
                confirmDelete = false
                onDone()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun HoursSlider(label: String, hours: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "${cycleLabel(hours)} ($hours h on)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = hours.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..24f,
            steps = 23,
        )
    }
}

@Composable
private fun NutrientEditor(nutrient: Nutrient, onChange: (Nutrient) -> Unit, onRemove: () -> Unit) {
    OutlinedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nutrient.name,
                    onValueChange = { onChange(nutrient.copy(name = it)) },
                    label = { Text("Product") },
                    placeholder = { Text("e.g. Grow A") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove nutrient") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nutrient.brand,
                    onValueChange = { onChange(nutrient.copy(brand = it)) },
                    label = { Text("Brand") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = nutrient.dose,
                    onValueChange = { onChange(nutrient.copy(dose = it)) },
                    label = { Text("Dose") },
                    placeholder = { Text("2 ml/L") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
