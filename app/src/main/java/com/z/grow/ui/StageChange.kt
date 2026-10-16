package com.z.grow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.z.grow.data.DeathReason
import com.z.grow.data.GrowStage
import com.z.grow.data.Plant
import java.time.LocalDate

/** Stages that ask for details (or confirmation) before switching. */
val GrowStage.needsDetails: Boolean
    get() = this == GrowStage.DRYING || this == GrowStage.CURING || this.isArchived

/** The plant moved into [stage] starting today. */
fun Plant.movedTo(stage: GrowStage): Plant = copy(
    stage = stage,
    stageStartEpochDay = LocalDate.now().toEpochDay(),
    deathReason = if (stage == GrowStage.DEAD) deathReason ?: DeathReason.OTHER else deathReason,
)

/** Fields for the current stage's details: drying days, curing weeks, or cause of death. */
@Composable
fun StageDetailsFields(plant: Plant, onChange: (Plant) -> Unit) {
    when (plant.stage) {
        GrowStage.DRYING -> NumberStepper(
            label = "Days to dry",
            value = plant.dryDays,
            range = 1..60,
            valueText = { "Done ${formatDay(plant.stageStart + it)}" },
        ) { onChange(plant.copy(dryDays = it)) }
        GrowStage.CURING -> NumberStepper(
            label = "Weeks to cure",
            value = plant.cureWeeks,
            range = 1..52,
            valueText = { "Done ${formatDay(plant.stageStart + it * 7L)}" },
        ) { onChange(plant.copy(cureWeeks = it)) }
        GrowStage.DEAD -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField("Cause", DeathReason.entries, plant.deathReason ?: DeathReason.OTHER, { it.label }) {
                onChange(plant.copy(deathReason = it))
            }
            OutlinedTextField(
                value = plant.deathNotes,
                onValueChange = { onChange(plant.copy(deathNotes = it)) },
                label = { Text("What happened? (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> Unit
    }
}

/** Confirms a move into a stage that needs details; returns the updated plant. */
@Composable
fun StageChangeDialog(plant: Plant, target: GrowStage, onDismiss: () -> Unit, onConfirm: (Plant) -> Unit) {
    var draft by remember { mutableStateOf(plant.movedTo(target)) }
    val (title, message, button) = when (target) {
        GrowStage.DRYING -> Triple("Start drying", "You'll get a reminder when drying should be done.", "Start drying")
        GrowStage.CURING -> Triple("Start curing", "You'll get a reminder when the cure is complete.", "Start curing")
        GrowStage.HARVESTED -> Triple(
            "Harvest ${plant.displayName}?",
            "It moves to the archive with its full history, and leaves the active plant list and calendar.",
            "Harvest",
        )
        else -> Triple(
            "Mark ${plant.displayName} as dead?",
            "It moves to the archive with its history. Recording the cause helps you avoid it next time.",
            "Move to archive",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message)
                StageDetailsFields(draft) { draft = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }) { Text(button) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
