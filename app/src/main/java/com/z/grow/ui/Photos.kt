package com.z.grow.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.z.grow.R
import com.z.grow.data.PhotoStore
import com.z.grow.data.Plant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoPicker(val takePhoto: () -> Unit, val choose: () -> Unit)

/** Camera and gallery launchers that deliver photo file names already copied into [PhotoStore]. */
@Composable
fun rememberPhotoPicker(maxItems: Int, onPhotos: (List<String>) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onPhotos)
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCameraFile?.let(::File)
        if (ok && file != null && file.length() > 0) deliver(listOf(file.name)) else file?.delete()
        pendingCameraFile = null
    }
    val import = { uris: List<android.net.Uri> ->
        scope.launch {
            val names = withContext(Dispatchers.IO) { uris.mapNotNull { PhotoStore.import(context, it) } }
            if (names.isNotEmpty()) deliver(names)
        }
        Unit
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { import(listOf(it)) }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, maxItems))) {
        import(it)
    }

    return remember {
        PhotoPicker(
            takePhoto = {
                val file = PhotoStore.newFile(context)
                pendingCameraFile = file.path
                try {
                    takePicture.launch(PhotoStore.uriFor(context, file))
                } catch (_: ActivityNotFoundException) {
                    pendingCameraFile = null
                    Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                }
            },
            choose = {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (maxItems == 1) pickOne.launch(request) else pickMany.launch(request)
            },
        )
    }
}

/** Round profile photo, or a sprout placeholder. */
@Composable
fun PlantAvatar(plant: Plant?, size: Int, modifier: Modifier = Modifier, fileName: String? = plant?.photoFileName) {
    val context = LocalContext.current
    val shape = CircleShape
    if (fileName != null) {
        AsyncImage(
            model = PhotoStore.file(context, fileName),
            contentDescription = plant?.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(
            modifier.size(size.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_sprout),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.55f).dp),
            )
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

/** Full-screen photo; [actionLabel] adds a button at the bottom (e.g. set as profile photo). */
@Composable
fun PhotoViewer(file: File, onDismiss: () -> Unit, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss)) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (actionLabel != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}
