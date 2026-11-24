package com.irontrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irontrack.app.ui.theme.DeltaDownYellow
import com.irontrack.app.ui.theme.DeltaUpBlue
import com.irontrack.core.util.PerformanceDelta

/** Différence en pourcentage avec la séance précédente : bleu si progression, jaune si baisse. */
@Composable
fun DeltaBadge(percent: Double, modifier: Modifier = Modifier) {
    val label = PerformanceDelta.format(percent)
    val color = when {
        label == "=" -> MaterialTheme.colorScheme.onSurfaceVariant
        percent > 0 -> DeltaUpBlue
        else -> DeltaDownYellow
    }
    Text(
        label,
        modifier = modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
