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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.z.grow.data.GrowNote
import com.z.grow.data.LightStatus
import com.z.grow.data.PlantTask
import com.z.grow.data.TaskKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val WeekDays = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun CalendarScreen(data: GrowData, vm: GrowViewModel, onAddEnvironment: () -> Unit, onAddPlant: () -> Unit) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var addingNote by remember { mutableStateOf(false) }
    val agenda = data.agenda(selected)

    Scaffold(topBar = { GrowTopBar("Grow with Green") }, contentWindowInsets = WindowInsets(0)) { padding ->
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
                TaskCard(task) { done -> vm.setTaskDone(task.plant.id, selected, task.kind, done) }
            }
            items(agenda.lights, key = { "light-${it.environment.id}" }) { LightCard(it) }
            items(agenda.notes, key = { "note-${it.id}" }) { note -> NoteCard(note) { vm.deleteNote(note) } }
            item {
                OutlinedButton(onClick = { addingNote = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add note for this day")
                }
            }
        }
    }

    if (addingNote) {
        AddNoteDialog(
            onDismiss = { addingNote = false },
            onAdd = {
                vm.addNote(selected, it)
                addingNote = false
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
            if (agenda.hasNotes) Dot(NoteColor)
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(WaterColor to "Water", FeedColor to "Feed", LightColor to "Light change", NoteColor to "Note").forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TaskCard(task: PlantTask, onDoneChange: (Boolean) -> Unit) {
    val accent = if (task.kind == TaskKind.FEED) FeedColor else WaterColor
    Card {
        Row(Modifier.padding(end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = task.done, onCheckedChange = onDoneChange)
            Column(Modifier.weight(1f).padding(top = 12.dp)) {
                Text(
                    "${task.kind.label} ${task.plant.displayName}",
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
            }
            Box(Modifier.padding(top = 18.dp)) { Dot(accent) }
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

@Composable
private fun NoteCard(note: GrowNote, onDelete: () -> Unit) {
    Card {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(NoteColor)
            Text(note.text, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Delete note") }
        }
    }
}

@Composable
private fun AddNoteDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Topped plants, pH 6.3, etc.") },
                minLines = 3,
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(text.trim()) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
