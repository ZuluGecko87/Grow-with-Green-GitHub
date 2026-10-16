package com.z.grow.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.z.grow.data.EntryKind
import com.z.grow.data.GrowData
import com.z.grow.data.JournalEntry
import com.z.grow.data.PhotoStore
import com.z.grow.data.Plant
import com.z.grow.data.TaskKind
import com.z.grow.data.TaskLog
import com.z.grow.data.TimelineItem
import java.time.LocalDate

@Composable
fun JournalScreen(data: GrowData, vm: GrowViewModel, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    var showLogs by rememberSaveable { mutableStateOf(true) }
    var editingLog by remember { mutableStateOf<TaskLog?>(null) }
    val items = data.timeline(filter, includeLogs = showLogs)

    Scaffold(
        topBar = { GrowTopBar("Journal") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New entry") },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (data.plants.isNotEmpty()) {
                item {
                    DropdownField(
                        label = "Show",
                        options = listOf<Plant?>(null) + data.plants,
                        selected = data.plant(filter),
                        optionLabel = { it?.displayName ?: "All plants" },
                    ) { filter = it?.id }
                }
            }
            item {
                FilterChip(
                    selected = showLogs,
                    onClick = { showLogs = !showLogs },
                    label = { Text("Waterings & feedings") },
                )
            }
            if (items.isEmpty()) {
                item { EmptyState("Nothing here yet.\nWaterings, photos and notes will build up a history as your plants grow.") }
            }
            items(items, key = { it.key }) { item ->
                TimelineCard(item, data, onOpenEntry = onOpen, onEditLog = { editingLog = it })
            }
        }
    }

    editingLog?.let { LogEditDialog(it, data, vm) { editingLog = null } }
}

private val TimelineItem.key: String
    get() = when (this) {
        is TimelineItem.Entry -> "entry-${entry.id}"
        is TimelineItem.Log -> "log-${log.id}"
    }

@Composable
fun TimelineCard(item: TimelineItem, data: GrowData, onOpenEntry: (Long) -> Unit, onEditLog: (TaskLog) -> Unit) {
    when (item) {
        is TimelineItem.Entry -> JournalEntryCard(item.entry, data) { onOpenEntry(item.entry.id) }
        is TimelineItem.Log -> LogCard(item.log, data) { onEditLog(item.log) }
    }
}

/** Edit or delete a logged watering/feeding. */
@Composable
fun LogEditDialog(log: TaskLog, data: GrowData, vm: GrowViewModel, onDismiss: () -> Unit) {
    LogTaskDialog(
        request = LogRequest(data.plant(log.plantId), log.kind, log),
        plants = data.plants,
        onDismiss = onDismiss,
        onSave = { plantId, kind, ph, ppm, amount ->
            vm.logTask(plantId, LocalDate.ofEpochDay(log.epochDay), kind, ph, ppm, amount)
            onDismiss()
        },
        onDelete = {
            vm.removeTaskLog(log)
            onDismiss()
        },
    )
}

@Composable
private fun LogCard(log: TaskLog, data: GrowData, onClick: () -> Unit) {
    val plant = data.plant(log.plantId)
    val accent = if (log.kind == TaskKind.FEED) FeedColor else WaterColor
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlantAvatar(plant, size = 36)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "${if (log.kind == TaskKind.FEED) "Fed" else "Watered"} ${plant?.displayName ?: ""}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(formatDay(log.epochDay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (log.readings.isNotEmpty()) {
                    Text(log.readings, style = MaterialTheme.typography.bodyMedium, color = accent, fontWeight = FontWeight.Medium)
                }
            }
            Dot(accent)
        }
    }
}

private val EntryKind.badge: String?
    get() = when (this) {
        EntryKind.NOTE -> null
        EntryKind.PLANT_ADDED -> "🌱 Plant added"
        EntryKind.PROFILE_PHOTO -> "📷 Profile photo"
        EntryKind.STAGE_CHANGE -> "🌿 Stage change"
    }

@Composable
fun JournalEntryCard(entry: JournalEntry, data: GrowData, onClick: () -> Unit) {
    val context = LocalContext.current
    val photos = data.photosFor(entry.id)
    val plant = data.plant(entry.plantId)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plant != null) {
                    PlantAvatar(plant, size = 36)
                } else {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).background(NoteColor, CircleShape))
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(plant?.displayName ?: "General", style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatDay(entry.epochDay) + (entry.kind.badge?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.text.isNotBlank()) Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumb(PhotoStore.file(context, photo.fileName), size = 96)
                    }
                }
            }
        }
    }
}

@Composable
fun JournalEditScreen(
    id: Long,
    initialDay: Long?,
    initialPlantId: Long?,
    data: GrowData,
    vm: GrowViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var form by remember {
        mutableStateOf(
            if (id == 0L) {
                JournalEntry(epochDay = initialDay ?: LocalDate.now().toEpochDay(), plantId = initialPlantId)
            } else {
                null
            },
        )
    }
    val photos = remember { mutableStateListOf<String>() }
    var original by remember { mutableStateOf(emptySet<String>()) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker(maxItems = 10) { photos.addAll(it) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val saved = vm.photosFor(id).map { it.fileName }
            photos.addAll(saved)
            original = saved.toSet()
            form = vm.journalEntry(id) ?: JournalEntry()
        }
    }

    // Leaving without saving drops photos added in this session.
    val discard = {
        PhotoStore.delete(context, photos.filter { it !in original })
        onDone()
    }
    BackHandler(onBack = discard)

    Scaffold(
        topBar = {
            GrowTopBar(if (id == 0L) "New journal entry" else "Journal entry", onBack = discard) {
                if (id != 0L) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete entry") }
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
            DateField("Date", f.epochDay) { form = f.copy(epochDay = it) }
            DropdownField(
                label = "Plant",
                options = listOf<Plant?>(null) + data.plants,
                selected = data.plant(f.plantId),
                optionLabel = { it?.displayName ?: "General (no plant)" },
            ) { form = f.copy(plantId = it?.id) }
            OutlinedTextField(
                value = f.text,
                onValueChange = { form = f.copy(text = it) },
                label = { Text("Notes") },
                placeholder = { Text("Topped today, first pistils showing, leaves look healthy…") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Photos")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = picker.takePhoto, modifier = Modifier.weight(1f)) { Text("Take photo") }
                OutlinedButton(onClick = picker.choose, modifier = Modifier.weight(1f)) { Text("Choose photos") }
            }
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos.toList(), key = { it }) { name ->
                        Box {
                            PhotoThumb(
                                PhotoStore.file(context, name),
                                size = 104,
                                modifier = Modifier.clickable { viewing = name },
                            )
                            IconButton(
                                onClick = {
                                    photos.remove(name)
                                    // Unsaved photos can go right away; saved ones are deleted on save.
                                    if (name !in original) PhotoStore.delete(context, listOf(name))
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                            ) { Icon(Icons.Filled.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
                Text(
                    "Tap a photo to view it or make it the plant's profile photo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    vm.saveJournalEntry(f.copy(text = f.text.trim()), photos.toList(), original.filter { it !in photos })
                    onDone()
                },
                enabled = f.text.isNotBlank() || photos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save entry") }
        }
    }

    viewing?.let { name ->
        val plant = data.plant(form?.plantId)
        PhotoViewer(
            file = PhotoStore.file(context, name),
            onDismiss = { viewing = null },
            actionLabel = plant?.let { "Make ${it.displayName}'s profile photo" },
            onAction = {
                plant?.let {
                    vm.setProfilePhoto(it.id, name)
                    Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT).show()
                }
                viewing = null
            },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            what = "this journal entry and its photos",
            onConfirm = {
                form?.let { vm.deleteJournalEntry(it) }
                PhotoStore.delete(context, photos.filter { it !in original })
                confirmDelete = false
                onDone()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
