package de.run.app.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class AppDropdownOption(
    val value: String,
    val title: String,
    val supportingText: String? = null
)

@Composable
fun AppDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<AppDropdownOption>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    anchor: @Composable (Modifier) -> Unit
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    Column(modifier) {
        anchor(Modifier)
        if (expanded) {
            Surface(
                modifier = menuModifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 360.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.title)
                                    option.supportingText?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            onClick = {
                                onExpandedChange(false)
                                onOptionSelected(option.value)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
