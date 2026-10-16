package com.z.grow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.z.grow.data.DayAgenda
import com.z.grow.data.GrowData
import com.z.grow.data.GrowStage
import com.z.grow.data.LightStatus
import com.z.grow.data.Milestone
import com.z.grow.data.Plant
import com.z.grow.data.PlantTask
import com.z.grow.data.TaskKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val WeekDays = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun CalendarScreen(
    data: GrowData,
    vm: GrowViewModel,
    onAddEnvironment: () -> Unit,
    onAddPlant: () -> Unit,
    onOpenJournal: (Long) -> Unit,
    onNewJournal: (epochDay: Long) -> Unit,
    onSettings: () -> Unit,
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var logging by remember { mutableStateOf<LogRequest?>(null) }
    var nextStage by remember { mutableStateOf<Pair<Plant, GrowStage>?>(null) }
    val agenda = data.agenda(selected)

    Scaffold(
        topBar = {
            GrowTopBar("Grow with Green") {
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (data.environments.isEmpty() || data.plants.isEmpty()) {
                item { GettingStarted(data, onAddEnvironment, onAddPlant) }
            }
            item {
                MonthGrid(
                    month = month,
                    selected = selected,
                    data = data,
                    onMonthChange = { month = it },
                    onSelect = { selected = it },
                )
            }
            item { Legend() }
            item {
                Text(
                    if (selected == LocalDate.now()) "Today · ${selected.format(LongDate)}" else selected.format(LongDate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (agenda.tasks.isEmpty()) {
                item { Text("No watering or feeding scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(agenda.tasks, key = { "${it.plant.id}-${it.kind}" }) { task ->
                TaskCard(
                    task = task,
                    onCheckedChange = { checked ->
                        if (checked) logging = LogRequest(task.plant, task.kind, null)
                        else task.log?.let(vm::removeTaskLog)
                    },
                    onClick = { logging = LogRequest(task.plant, task.kind, task.log) },
                )
            }
            items(agenda.milestones, key = { "milestone-${it.plant.id}" }) { milestone ->
                MilestoneCard(milestone) { stage -> nextStage = milestone.plant to stage }
            }
            items(agenda.lights, key = { "light-${it.environment.id}" }) { LightCard(it) }
            items(agenda.journal, key = { "journal-${it.id}" }) { entry ->
                JournalEntryCard(entry, data) { onOpenJournal(entry.id) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { logging = LogRequest(null, TaskKind.WATER, null) },
                        enabled = data.activePlants.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Log watering") }
                    OutlinedButton(onClick = { onNewJournal(selected.toEpochDay()) }, modifier = Modifier.weight(1f)) {
                        Text("Journal entry")
                    }
                }
            }
        }
    }

    nextStage?.let { (plant, stage) ->
        StageChangeDialog(
            plant = plant,
            target = stage,
            onDismiss = { nextStage = null },
            onConfirm = {
                vm.savePlant(it, original = plant)
                nextStage = null
            },
        )
    }

    logging?.let { request ->
        LogTaskDialog(
            request = request,
            plants = data.activePlants.filter { it.stage.isGrowing },
            onDismiss = { logging = null },
            onSave = { plantId, kind, ph, ppm, amount ->
                vm.logTask(plantId, selected, kind, ph, ppm, amount)
                logging = null
            },
        )
    }
}

@Composable
private fun GettingStarted(data: GrowData, onAddEnvironment: () -> Unit, onAddPlant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Get growing 🌱", style = MaterialTheme.typography.titleMedium)
            Text("Set up an environment with its medium, lights and nutrients, then add plants with a watering and feeding schedule. Everything shows up here on the calendar.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.environments.isEmpty()) Button(onClick = onAddEnvironment) { Text("Add environment") }
                if (data.plants.isEmpty()) OutlinedButton(onClick = onAddPlant) { Text("Add plant") }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    data: GrowData,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    // Sunday-first grid: DayOfWeek.value is 1 (Mon)..7 (Sun).
    val leading = month.atDay(1).dayOfWeek.value % 7
    val days = month.lengthOfMonth()
    val rows = (leading + days + 6) / 7

    Card {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
                }
                Text(
                    month.format(MonthTitle),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                }
            }
            Row {
                WeekDays.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (row in 0 until rows) {
                Row {
                    for (col in 0 until 7) {
                        val dayNumber = row * 7 + col - leading + 1
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                agenda = data.agenda(date),
                                isSelected = date == selected,
                                isToday = date == today,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(date) },
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    agenda: DayAgenda,
    isSelected: Boolean,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        isSelected -> colors.primary
        isToday -> colors.primaryContainer
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            color = if (isSelected) colors.onPrimary else colors.onSurface,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp)) {
            if (agenda.hasWater) Dot(WaterColor)
            if (agenda.hasFeed) Dot(FeedColor)
            if (agenda.hasFlip) Dot(LightColor)
            if (agenda.hasJournal) Dot(NoteColor)
            if (agenda.hasMilestone) Dot(MilestoneColor)
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(WaterColor to "Water", FeedColor to "Feed", LightColor to "Lights", NoteColor to "Journal", MilestoneColor to "Harvest").forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TaskCard(task: PlantTask, onCheckedChange: (Boolean) -> Unit, onClick: () -> Unit) {
    val accent = if (task.kind == TaskKind.FEED) FeedColor else WaterColor
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = task.done, onCheckedChange = onCheckedChange)
            PlantAvatar(task.plant, size = 40, modifier = Modifier.padding(top = 4.dp, end = 12.dp))
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(
                    "${task.kind.label} ${task.plant.displayName}" + if (task.scheduled) "" else " (extra)",
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                val where = task.environment?.let { "${it.name} · ${it.medium.label}" } ?: "No environment"
                Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.kind == TaskKind.FEED) {
                    if (task.nutrients.isEmpty()) {
                        Text("No nutrients set for this environment", style = MaterialTheme.typography.bodySmall)
                    }
                    task.nutrients.forEach { n ->
                        val brand = if (n.brand.isBlank()) "" else " (${n.brand})"
                        val dose = if (n.dose.isBlank()) "" else " — ${n.dose}"
                        Text("• ${n.name}$brand$dose", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val readings = task.log?.readings.orEmpty()
                if (readings.isNotEmpty()) {
                    Text(readings, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Medium)
                }
            }
            Box(Modifier.padding(top = 18.dp)) { Dot(accent) }
        }
    }
}

@Composable
private fun MilestoneCard(milestone: Milestone, onNextStage: (GrowStage) -> Unit) {
    val plant = milestone.plant
    val next = if (plant.stage == GrowStage.DRYING) GrowStage.CURING else GrowStage.HARVESTED
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlantAvatar(plant, size = 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(milestone.text, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (next == GrowStage.CURING) "Jar it up and start the cure." else "Ready to call it harvested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MilestoneColor,
                )
            }
            Button(onClick = { onNextStage(next) }) {
                Text(if (next == GrowStage.CURING) "Start cure" else "Harvest")
            }
        }
    }
}

@Composable
private fun LightCard(light: LightStatus) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("💡 ${light.environment.name}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Lights ${formatMinute(light.environment.lightsOnMinute)}–${formatMinute(light.lightsOffMinute)} · ${cycleLabel(light.hoursOn)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (light.isFlipDay) {
                Text(
                    "Switch lights to ${cycleLabel(light.hoursOn)} today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LightColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
