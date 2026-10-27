package de.run.app.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.run.app.presentation.components.ExpandableSection

@Composable
fun PlanGuidanceSection(lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    ExpandableSection(
        title = "Wie dein Plan weiterwächst",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
        titleStyle = MaterialTheme.typography.titleSmall
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
