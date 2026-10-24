package de.run.app.presentation.strength

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.run.app.R
import de.run.app.domain.model.StrengthCatalogExercise
import de.run.app.domain.model.StrengthSessionProgress
import de.run.app.domain.model.StrengthWorkout
import de.run.app.presentation.components.AppDropdown
import de.run.app.presentation.components.AppDropdownOption

@Composable
fun StrengthScreen(
    viewModel: StrengthViewModel,
    onBack: () -> Unit,
    onDetailChanged: (Boolean) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val catalog = state.catalog
    var detailExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    var infoText by rememberSaveable { mutableStateOf<String?>(null) }
    val workout = catalog?.workouts?.firstOrNull { it.id == selectedWorkoutId }
        ?: state.selectedSession?.let { session -> catalog?.workouts?.firstOrNull { it.id == session.workoutId } }
        ?: catalog?.workouts?.firstOrNull()
    val showingDetail = detailExerciseId != null
    BackHandler(enabled = showingDetail) {
        detailExerciseId = null
        onDetailChanged(false)
    }
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
        return
    }
    if (catalog == null || workout == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.errorMessage ?: "Der Übungskatalog ist gerade nicht verfügbar.")
            OutlinedButton(onClick = onBack) { Text("Zurück") }
        }
        return
    }
    val matchingSession = state.sessions.firstOrNull {
        it.workoutId == workout.id && it.status == StrengthSessionProgress.Status.IN_PROGRESS
    }
    val visibleSession = state.selectedSession?.takeIf { it.workoutId == workout.id } ?: matchingSession
    val contentCatalog = visibleSession?.let(viewModel::catalogFor) ?: catalog
    val contentWorkout = contentCatalog.workouts.firstOrNull { it.id == workout.id } ?: workout

    if (detailExerciseId == null) {
        StrengthOverview(
            workout = contentWorkout,
            catalog = contentCatalog,
            session = visibleSession,
            onBack = onBack,
            onSelectWorkout = { selectedWorkoutId = it },
            menuExpanded = moreMenu,
            onMenuChange = { moreMenu = it },
            onShowInfo = { infoText = catalog.safetyAdvice },
            onStart = {
                detailExerciseId = workout.exerciseIds.firstOrNull()
                onDetailChanged(true)
                viewModel.startOrResume(workout.id)
            },
            onExercise = { exerciseId ->
                val inProgress = visibleSession
                val nextId = if (inProgress != null) inProgress.currentExerciseId ?: exerciseId else exerciseId
                detailExerciseId = nextId
                onDetailChanged(true)
                viewModel.startOrResume(contentWorkout.id, nextId)
            }
        )
    } else {
        val session = visibleSession
        val selectedExerciseId = session?.takeIf { it.status == StrengthSessionProgress.Status.IN_PROGRESS }
            ?.let { it.currentExerciseId }
            ?: detailExerciseId
        val exercise = contentCatalog.exercisesById[selectedExerciseId]
        if (exercise == null) {
            Text("Diese Übung ist nicht im Katalog enthalten.", Modifier.padding(24.dp))
        } else {
            StrengthExerciseDetail(
                exercise = exercise,
                workout = contentWorkout,
                catalog = contentCatalog,
                session = session,
                remainingSeconds = state.remainingSeconds,
                menuExpanded = moreMenu,
                onMenuChange = { moreMenu = it },
                onShowInfo = {
                    infoText = buildList {
                        add(exercise.description)
                        exercise.techniqueCues.forEach { add("Technik: $it") }
                        exercise.commonMistakes.forEach { add("Vermeide: $it") }
                        exercise.cautions.forEach { add("Hinweis: $it") }
                        add("Leichtere Variante: ${exercise.regression}")
                        add("Steigerung: ${exercise.progression}")
                    }.joinToString("\n\n")
                },
                onBack = { detailExerciseId = null; onDetailChanged(false) },
                onStartSession = { viewModel.startWorkoutAndStartSet(contentWorkout.id, exercise.id) },
                onStartSet = viewModel::startCurrentSet,
                onCompleteSet = viewModel::completeCurrentSet,
                onFinishRest = viewModel::finishRest,
                onSkipOptional = viewModel::skipOptionalExercise,
                onGoOverview = { detailExerciseId = null; onDetailChanged(false) }
            )
        }
    }
    state.errorMessage?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
    infoText?.let { message ->
        AlertDialog(
            onDismissRequest = { infoText = null },
            title = { Text("Hinweise zum Krafttraining") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { infoText = null }) { Text("Schließen") } }
        )
    }
}

@Composable
private fun StrengthOverview(
    workout: StrengthWorkout,
    catalog: de.run.app.domain.model.StrengthCatalog,
    session: StrengthSessionProgress?,
    onBack: () -> Unit,
    onSelectWorkout: (String) -> Unit,
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
    onStart: () -> Unit,
    onExercise: (String) -> Unit
) {
    val requiredIds = workout.exerciseIds.filterNot(workout.optionalExerciseIds::contains)
    val completed = session?.completedSets.orEmpty()
    val completeExercises = requiredIds.count { id -> completed.count { it.exerciseId == id } >= workout.rounds }
    val completeSets = completed.count { it.exerciseId in requiredIds }.coerceAtMost(requiredIds.size * workout.rounds)
    val totalSets = (requiredIds.size * workout.rounds).coerceAtLeast(1)
    var programMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.weight(1f))
                    Box {
                        Text("•••", Modifier.clickable { onMenuChange(true) }.padding(10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChange(false) }) {
                            DropdownMenuItem(text = { Text("Hinweise zum Krafttraining") }, onClick = { onMenuChange(false); onShowInfo() })
                        }
                    }
                }
                Text("Kraft für deinen Lauf", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Heute · ca. ${workout.estimatedMinutes} Min.", style = MaterialTheme.typography.titleMedium)
                        Text("${workout.exerciseIds.size} Übungen · ${workout.rounds} Sätze je Übung", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Image(
                        painter = painterResource(R.drawable.run_training_strength),
                        contentDescription = "LAP-Schildkröte",
                        modifier = Modifier.size(96.dp), contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(8.dp))
                AppDropdown(
                    expanded = programMenu,
                    onExpandedChange = { programMenu = it },
                    options = catalog.workouts.map { AppDropdownOption(it.id, it.title, it.subtitle) },
                    onOptionSelected = onSelectWorkout,
                    modifier = Modifier.fillMaxWidth()
                ) { anchorModifier ->
                    Card(
                        anchorModifier.fillMaxWidth().clickable { programMenu = !programMenu },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(workout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(workout.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("⌄", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                Text(workout.description, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        requiredIds.forEach { id ->
                            val isDone = completed.count { it.exerciseId == id } >= workout.rounds
                            Box(Modifier.size(14.dp).clip(CircleShape).background(if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    Text("$completeExercises von ${requiredIds.size} Übungen", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { (completeSets.toFloat() / totalSets).coerceIn(0f, 1f) },
                    Modifier.fillMaxWidth().padding(top = 7.dp).height(5.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
            }
            itemsIndexed(workout.exerciseIds, key = { _, id -> id }) { _, exerciseId ->
                val exercise = catalog.exercisesById.getValue(exerciseId)
                val done = completed.count { it.exerciseId == exerciseId } >= workout.rounds
                StrengthExerciseCard(exercise, workout.rounds, done, exerciseId in workout.optionalExerciseIds) { onExercise(exerciseId) }
            }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 10.dp)) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (session != null) "Training fortsetzen   →" else "Training starten   →")
            }
        }
    }
}

@Composable
private fun StrengthExerciseCard(exercise: StrengthCatalogExercise, rounds: Int, done: Boolean, optional: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StrengthIllustration(exercise, Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(exercise.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(exercise.prescription.summary(rounds), style = MaterialTheme.typography.bodySmall)
                Text(if (done) "Abgeschlossen" else if (optional) "Optional" else "Offen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(if (done) "✓" else "›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StrengthExerciseDetail(
    exercise: StrengthCatalogExercise,
    workout: StrengthWorkout,
    catalog: de.run.app.domain.model.StrengthCatalog,
    session: StrengthSessionProgress?,
    remainingSeconds: Int,
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
    onBack: () -> Unit,
    onStartSession: () -> Unit,
    onStartSet: () -> Unit,
    onCompleteSet: () -> Unit,
    onFinishRest: () -> Unit,
    onSkipOptional: () -> Unit,
    onGoOverview: () -> Unit
) {
    val exerciseIndex = workout.exerciseIds.indexOf(exercise.id).coerceAtLeast(0)
    val active = session?.status == StrengthSessionProgress.Status.IN_PROGRESS
    val isCurrent = active && session!!.currentExerciseId == exercise.id
    val finished = session?.status == StrengthSessionProgress.Status.COMPLETED
    val setsDone = session?.completedSets?.count { it.exerciseId == exercise.id } ?: 0
    val round = (setsDone + 1).coerceAtMost(workout.rounds)
    val inRest = isCurrent && session?.pauseUntilMillis != null && !session.isCurrentSetStarted
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 10.dp), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text("Übung ${exerciseIndex + 1} von ${workout.exerciseIds.size}")
            Spacer(Modifier.weight(1f))
            Box {
                Text("•••", Modifier.clickable { onMenuChange(true) }.padding(8.dp))
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChange(false) }) {
                    DropdownMenuItem(text = { Text("Technik und Hinweise") }, onClick = { onMenuChange(false); onShowInfo() })
                }
            }
        }
        LinearProgressIndicator(
            progress = {
                val requiredSetCount = workout.exerciseIds.filterNot(workout.optionalExerciseIds::contains).size * workout.rounds
                val doneSetCount = session?.completedSets.orEmpty().count { it.exerciseId !in workout.optionalExerciseIds }
                (doneSetCount.toFloat() / requiredSetCount.coerceAtLeast(1)).coerceIn(0f, 1f)
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).height(5.dp).clip(CircleShape)
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StrengthIllustration(exercise, Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow))
                Text(exercise.title, Modifier.fillMaxWidth().padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(exercise.subtitle, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailMetric("Sätze", "${workout.rounds}", Modifier.weight(1f))
                    DetailMetric("Umfang", exercise.prescription.summary(workout.rounds), Modifier.weight(2f))
                    DetailMetric("Pause", "${exercise.prescription.restSeconds} s", Modifier.weight(1f))
                }
                if (exercise.equipment.isNotEmpty()) {
                    Text("Benötigt: ${exercise.equipment.joinToString { it.toEquipmentLabel() }}", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Ablauf", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                exercise.steps.forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${index + 1}", Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(step, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tipp", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(exercise.tip)
                    }
                }
                if (exercise.techniqueCues.isNotEmpty() || exercise.cautions.isNotEmpty()) {
                    var showTips by remember(exercise.id) { mutableStateOf(false) }
                    Text(if (showTips) "Technik und Hinweise  ⌃" else "Technik und Hinweise  ⌄", Modifier.fillMaxWidth().clickable { showTips = !showTips }.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.primary)
                    if (showTips) {
                        exercise.techniqueCues.forEach { Text("• $it") }
                        exercise.cautions.forEach { Text("Hinweis: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (finished) {
                Text("Training abgeschlossen", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.primary)
                Button(onClick = onGoOverview, Modifier.fillMaxWidth().height(52.dp)) { Text("Zur Übersicht") }
            } else if (!active) {
                Button(onClick = onStartSession, Modifier.fillMaxWidth().height(52.dp)) { Text("Satz 1 starten") }
            } else if (!isCurrent) {
                Button(onClick = onGoOverview, Modifier.fillMaxWidth().height(52.dp)) { Text("Zur aktuellen Übung") }
            } else if (inRest) {
                Text(if (remainingSeconds > 0) "Pause · ${remainingSeconds}s" else "Pause beendet", Modifier.align(Alignment.CenterHorizontally))
                Button(onClick = onFinishRest, Modifier.fillMaxWidth().height(52.dp)) { Text(if (remainingSeconds > 0) "Pause beenden" else "Weiter") }
            } else if (session?.isCurrentSetStarted == true && exercise.prescription.type == de.run.app.domain.model.StrengthPrescription.Type.HOLD) {
                Text("Haltezeit · ${remainingSeconds}s", Modifier.align(Alignment.CenterHorizontally))
            OutlinedButton(onClick = onCompleteSet, Modifier.fillMaxWidth().height(50.dp), enabled = remainingSeconds == 0) { Text("Satz abschließen") }
            } else if (session?.isCurrentSetStarted == true) {
                Button(onClick = onCompleteSet, Modifier.fillMaxWidth().height(52.dp)) { Text("Satz $round abschließen") }
            } else {
                Button(onClick = onStartSet, Modifier.fillMaxWidth().height(52.dp)) { Text("Satz $round starten") }
            }
            if (active && isCurrent && exercise.id in workout.optionalExerciseIds) {
                OutlinedButton(onClick = onSkipOptional, Modifier.fillMaxWidth()) { Text("Optionale Übung überspringen") }
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StrengthIllustration(exercise: StrengthCatalogExercise, modifier: Modifier = Modifier) {
    val drawable = StrengthIllustrationAssets.drawableFor(exercise.media.imageKey)
    if (drawable != null) {
        Image(painterResource(drawable), exercise.media.altText, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LAP", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Illustration folgt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun de.run.app.domain.model.StrengthPrescription.summary(rounds: Int): String {
    val amount = when (type) {
        de.run.app.domain.model.StrengthPrescription.Type.REPETITIONS -> "$reps Wiederholungen" +
            (holdPerRepSeconds?.let { " · je ${it}s halten" } ?: "") + (tempo?.let { " · Tempo $it" } ?: "")
        de.run.app.domain.model.StrengthPrescription.Type.HOLD -> "${holdSeconds}s halten"
    }
    return "$rounds Sätze · $amount${if (perSide) " je Seite" else ""}"
}

private fun String.toEquipmentLabel(): String = when (this) {
    "mini_band" -> "Miniband"
    "long_band" -> "langes Band"
    "wall" -> "Wand"
    "step" -> "Stufe"
    "stable_band_anchor" -> "stabiler Bandanker"
    "optional_support" -> "optionale Stütze"
    "foot_cuff" -> "Fußschlaufe"
    else -> this
}
