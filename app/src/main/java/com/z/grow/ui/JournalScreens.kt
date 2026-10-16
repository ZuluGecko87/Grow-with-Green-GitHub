package com.z.grow.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.z.grow.data.GrowData
import com.z.grow.data.JournalEntry
import com.z.grow.data.PhotoStore
import com.z.grow.data.Plant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun JournalScreen(data: GrowData, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    val entries = data.journal.filter { filter == null || it.plantId == filter }

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
            if (entries.isEmpty()) {
                item { EmptyState("No journal entries yet.\nAdd notes and photos as your plants grow.") }
            }
            items(entries, key = { it.id }) { entry ->
                JournalEntryCard(entry, data) { onOpen(entry.id) }
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: JournalEntry, data: GrowData, onClick: () -> Unit) {
    val context = LocalContext.current
    val photos = data.photosFor(entry.id)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(NoteColor, CircleShape))
                Text(
                    formatDay(entry.epochDay) + (data.plant(entry.plantId)?.let { " · ${it.displayName}" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (entry.text.isNotBlank()) Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumb(PhotoStore.file(context, photo.fileName), size = 72)
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumb(file: File, size: Int, modifier: Modifier = Modifier) {
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun PhotoViewer(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
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
    val scope = rememberCoroutineScope()
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
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }

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

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCameraFile?.let(::File)
        if (ok && file != null && file.length() > 0) photos.add(file.name) else file?.delete()
        pendingCameraFile = null
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        scope.launch {
            val imported = withContext(Dispatchers.IO) { uris.mapNotNull { PhotoStore.import(context, it) } }
            photos.addAll(imported)
        }
    }

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
                OutlinedButton(
                    onClick = {
                        val file = PhotoStore.newFile(context)
                        pendingCameraFile = file.path
                        try {
                            takePicture.launch(PhotoStore.uriFor(context, file))
                        } catch (_: ActivityNotFoundException) {
                            pendingCameraFile = null
                            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Take photo") }
                OutlinedButton(
                    onClick = {
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Choose photos") }
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

    viewing?.let { PhotoViewer(PhotoStore.file(context, it)) { viewing = null } }

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
