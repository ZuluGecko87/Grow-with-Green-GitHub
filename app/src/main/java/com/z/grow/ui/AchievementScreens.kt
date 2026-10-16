package com.z.grow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.z.grow.data.Achievement
import com.z.grow.data.Achievements
import com.z.grow.data.EarnedAchievement
import com.z.grow.data.GrowData

@Composable
fun AchievementsScreen(data: GrowData, onBack: () -> Unit) {
    val earnedById = data.achievements.groupBy { it.achievementId }
    val unlocked = Achievements.all.filter { it.id in earnedById }
    val locked = Achievements.all.filter { it.id !in earnedById }

    Scaffold(topBar = { GrowTopBar("Achievements", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${unlocked.size} of ${Achievements.all.size} unlocked",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { unlocked.size.toFloat() / Achievements.all.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(unlocked, key = { it.id }) { AchievementRow(it, earnedById[it.id].orEmpty(), data) }
            if (locked.isNotEmpty()) {
                item { SectionTitle("Locked") }
                items(locked, key = { it.id }) { AchievementRow(it, emptyList(), data) }
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, earned: List<EarnedAchievement>, data: GrowData) {
    val isUnlocked = earned.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(achievement, size = 52, modifier = Modifier.alpha(if (isUnlocked) 1f else 0.3f))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                if (isUnlocked) {
                    val first = earned.minBy { it.earnedEpochDay }
                    val who = when {
                        achievement.perPlant -> " · ${earned.size} plant${if (earned.size == 1) "" else "s"}"
                        else -> data.plant(first.plantId)?.let { " · ${it.displayName}" }.orEmpty()
                    }
                    Text(
                        "Unlocked ${formatDay(first.earnedEpochDay)}$who",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (achievement.perPlant) {
                    Text("Can be earned by every plant", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun Badge(achievement: Achievement, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(achievement.emoji, fontSize = (size * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

/** Badges a plant has earned; tap one to see what it's for. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(earned: List<EarnedAchievement>, modifier: Modifier = Modifier) {
    val badges = earned.mapNotNull { e -> Achievements.byId(e.achievementId)?.let { it to e } }
    if (badges.isEmpty()) return
    var showing by remember { mutableStateOf<Pair<Achievement, EarnedAchievement>?>(null) }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        badges.forEach { pair -> Badge(pair.first, size = 30, modifier = Modifier.clickable { showing = pair }) }
    }
    showing?.let { (achievement, e) ->
        AlertDialog(
            onDismissRequest = { showing = null },
            icon = { Badge(achievement, size = 64) },
            title = { Text(achievement.title) },
            text = { Text("${achievement.description}\n\nUnlocked ${formatDay(e.earnedEpochDay)}") },
            confirmButton = { TextButton(onClick = { showing = null }) { Text("Nice") } },
        )
    }
}
