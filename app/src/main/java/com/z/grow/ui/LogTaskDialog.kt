package com.z.grow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.z.grow.data.Plant
import com.z.grow.data.TaskKind
import com.z.grow.data.TaskLog

/** What to log: a known plant and kind (from a calendar task), or [plant] null to let the user pick. */
data class LogRequest(val plant: Plant?, val kind: TaskKind, val existing: TaskLog?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTaskDialog(
    request: LogRequest,
    plants: List<Plant>,
    onDismiss: () -> Unit,
    onSave: (plantId: Long, kind: TaskKind, ph: Double?, ppm: Int?, amount: String) -> Unit,
) {
    var plant by remember { mutableStateOf(request.plant ?: plants.firstOrNull()) }
    var kind by remember { mutableStateOf(request.kind) }
    var ph by remember { mutableStateOf(request.existing?.ph?.let { "%.1f".format(it) } ?: "") }
    var ppm by remember { mutableStateOf(request.existing?.ppm?.toString() ?: "") }
    var amount by remember { mutableStateOf(request.existing?.amount ?: "") }

    val phValue = ph.replace(',', '.').toDoubleOrNull()
    val phError = ph.isNotBlank() && (phValue == null || phValue !in 0.0..14.0)
    val ppmValue = ppm.toIntOrNull()
    val ppmError = ppm.isNotBlank() && (ppmValue == null || ppmValue < 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(request.plant?.let { "${kind.label} ${it.displayName}" } ?: "Log watering or feeding")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (request.plant == null) {
                    DropdownField("Plant", plants, plant, { it?.displayName ?: "Choose a plant" }) { plant = it }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        TaskKind.entries.forEachIndexed { index, k ->
                            SegmentedButton(
                                selected = kind == k,
                                onClick = { kind = k },
                                shape = SegmentedButtonDefaults.itemShape(index, TaskKind.entries.size),
                            ) { Text(k.label) }
                        }
                    }
                }
                Text("Readings are optional.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ph,
                        onValueChange = { ph = it.take(5) },
                        label = { Text("pH") },
                        placeholder = { Text("6.3") },
                        isError = phError,
                        supportingText = if (phError) ({ Text("0–14") }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = ppm,
                        onValueChange = { ppm = it.filter(Char::isDigit).take(5) },
                        label = { Text("PPM") },
                        placeholder = { Text("650") },
                        isError = ppmError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    placeholder = { Text("e.g. 1 gal or 2 L") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { plant?.let { onSave(it.id, kind, phValue, ppmValue, amount.trim()) } },
                enabled = plant != null && !phError && !ppmError,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
