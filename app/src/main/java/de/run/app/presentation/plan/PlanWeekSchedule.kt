package de.run.app.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.run.app.R
import de.run.app.domain.model.PlannedRun
import de.run.app.domain.model.AvailabilityMode
import de.run.app.domain.model.AvailabilityStatus
import de.run.app.domain.model.TrainingWeek
import de.run.app.presentation.components.RunIllustration
import java.time.DayOfWeek
import java.util.Locale

@Composable
internal fun PlanWeekSchedule(
    week: TrainingWeek,
    availabilityMode: AvailabilityMode,
    onOpenStrength: () -> Unit,
    onOpenTrainingDetails: (PlannedRun) -> Unit
) {
    if (availabilityMode == AvailabilityMode.VARIABLE) {
        FlexibleWeekSuggestions(week, onOpenStrength, onOpenTrainingDetails)
        return
    }
    val entries = remember(week) { entriesForWeek(week) }

    Column(Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ScheduleEntryRow(
                entry = entry,
                onClick = {
                    if (entry.opensStrength) onOpenStrength()
                    else entry.run?.let(onOpenTrainingDetails)
                }
            )
        }
        if (week.scheduledRuns.isEmpty() && week.runs.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Vorschlag ohne festen Termin", style = MaterialTheme.typography.titleSmall)
                week.runs.forEach { run ->
                    Text(
                        "${run.title} · ca. ${run.totalMinutes} Min.  ›",
                        Modifier.fillMaxWidth().clickable { onOpenTrainingDetails(run) }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun FlexibleWeekSuggestions(
    week: TrainingWeek,
    onOpenStrength: () -> Unit,
    onOpenTrainingDetails: (PlannedRun) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Deine Einheiten ohne festen Termin", style = MaterialTheme.typography.titleSmall)
        week.runs.forEach { run ->
            Text(
                "${run.title} · ca. ${run.totalMinutes} Min.  ›",
                Modifier.fillMaxWidth().clickable { onOpenTrainingDetails(run) }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (week.strengthSessions > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Krafttraining · ${week.strengthSessions}× diese Woche  ›",
                Modifier.fillMaxWidth().clickable(onClick = onOpenStrength).padding(vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text("Verfügbare Zeit bestätigst du erst, wenn du trainieren möchtest.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleEntryRow(entry: ScheduleEntry, onClick: () -> Unit) {
    val isRun = entry.run != null
    val rowColor = if (isRun && !entry.isOptional) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.background
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(rowColor)
            .then(
                if (entry.run != null || entry.opensStrength) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .semantics {
                contentDescription = when {
                    entry.run != null -> "${entry.dayLabel}, ${entry.title}, ${entry.subtitle}. Trainingsdetails öffnen"
                    entry.opensStrength -> "${entry.dayLabel}, Krafttraining öffnen"
                    else -> "${entry.dayLabel}, Ruhetag"
                }
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.size(width = 38.dp, height = 42.dp), verticalArrangement = Arrangement.Center) {
            Text(entry.dayLabel, style = MaterialTheme.typography.labelLarge)
            Text(entry.dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EntryIcon(entry)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.run != null || entry.opensStrength) {
            Text("›", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun EntryIcon(entry: ScheduleEntry) {
    val resource = entry.iconResource
    if (resource != null) {
        RunIllustration(resource, entry.title, size = 38.dp)
    } else {
        androidx.compose.foundation.layout.Box(
            Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {}
    }
}

private data class ScheduleEntry(
    val id: String,
    val dayLabel: String,
    val dateLabel: String,
    val title: String,
    val subtitle: String,
    val run: PlannedRun? = null,
    val iconResource: Int? = null,
    val opensStrength: Boolean = false,
    val isOptional: Boolean = false
)

private fun entriesForWeek(week: TrainingWeek): List<ScheduleEntry> {
    val runsByDay = week.scheduledRuns.associate { it.day to it.run }
    val strengthDays = DayOfWeek.entries.filter {
        it !in runsByDay && week.availability[it] == AvailabilityStatus.AVAILABLE
    }.take(week.strengthSessions).toSet()
    var strengthNumber = 0
    return DayOfWeek.entries.map { day ->
        val run = runsByDay[day]
        when {
            run != null && run.title.contains("langer", ignoreCase = true) -> runEntry(
                week, day, run, R.drawable.run_training_longrun, "Langer Lauf", run.optional
            )
            run != null && run.optional -> runEntry(
                week, day, run, R.drawable.run_training_regeneration, "Optionaler Lauf", true
            )
            run != null -> runEntry(week, day, run, R.drawable.run_training_relaxed, "Lockerer Lauf")
            day in strengthDays -> strengthEntry(week, day, ++strengthNumber)
            else -> availabilityEntry(week, day)
        }
    }
}

private fun restEntry(week: TrainingWeek, day: DayOfWeek) = entry(week, day, "Ruhetag", "Erholung")

private fun availabilityEntry(week: TrainingWeek, day: DayOfWeek): ScheduleEntry = when (week.availability[day] ?: AvailabilityStatus.UNKNOWN) {
    AvailabilityStatus.UNKNOWN -> entry(week, day, "Noch offen", "Verfügbarkeit nicht eingetragen")
    AvailabilityStatus.UNAVAILABLE -> entry(week, day, "Keine Zeit", "Nicht verfügbar")
    AvailabilityStatus.AVAILABLE -> entry(week, day, "Verfügbar", "Kein Lauf vorgeschlagen")
}

private fun strengthEntry(week: TrainingWeek, day: DayOfWeek, session: Int) = entry(
    week,
    day,
    "Krafttraining",
    "Knie · Sprunggelenke · Rumpf",
    iconResource = R.drawable.run_training_strength,
    opensStrength = true,
    idSuffix = "kraft-$session"
)

private fun runEntry(
    week: TrainingWeek,
    day: DayOfWeek,
    run: PlannedRun?,
    icon: Int,
    title: String,
    optional: Boolean = false
): ScheduleEntry {
    val subtitle = when {
        run == null -> "Heute keine Laufeinheit"
        optional -> "Freiwillig · locker · ca. ${run.totalMinutes} Min."
        title == "Langer Lauf" -> "Ruhig · ca. ${run.totalMinutes} Min."
        else -> "Locker · ca. ${run.totalMinutes} Min."
    }
    return entry(week, day, title, subtitle, run, icon, isOptional = optional)
}

private fun entry(
    week: TrainingWeek,
    day: DayOfWeek,
    title: String,
    subtitle: String,
    run: PlannedRun? = null,
    iconResource: Int? = null,
    opensStrength: Boolean = false,
    isOptional: Boolean = false,
    idSuffix: String = title
): ScheduleEntry {
    val date = week.startsOn.plusDays((day.value - DayOfWeek.MONDAY.value).toLong())
    val locale = Locale.GERMANY
    return ScheduleEntry(
        id = "${date}-$idSuffix",
        dayLabel = when (day) {
            DayOfWeek.MONDAY -> "Mo"
            DayOfWeek.TUESDAY -> "Di"
            DayOfWeek.WEDNESDAY -> "Mi"
            DayOfWeek.THURSDAY -> "Do"
            DayOfWeek.FRIDAY -> "Fr"
            DayOfWeek.SATURDAY -> "Sa"
            DayOfWeek.SUNDAY -> "So"
        },
        dateLabel = date.dayOfMonth.toString(),
        title = title,
        subtitle = subtitle,
        run = run,
        iconResource = iconResource,
        opensStrength = opensStrength,
        isOptional = isOptional
    )
}
