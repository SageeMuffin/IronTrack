package com.irontrack.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.irontrack.core.util.FrenchFormat

/** Point d'une courbe : x = date (epoch ms), y = valeur. */
data class ChartPoint(val x: Long, val y: Double)

/** Courbe simple et lisible (sans dépendance externe). */
@Composable
fun LineChart(points: List<ChartPoint>, title: String, unit: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (points.size < 2) {
            Text(
                if (points.size == 1) "${FrenchFormat.number(points[0].y, 1)} $unit" else "—",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        Row {
            Text("${FrenchFormat.number(minY, 1)} – ${FrenchFormat.number(maxY, 1)} $unit",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = "$title : ${FrenchFormat.number(points.first().y, 1)} → ${FrenchFormat.number(points.last().y, 1)} $unit" }
        ) {
            val pad = 8.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val span = (maxY - minY).takeIf { it > 0 } ?: 1.0
            fun px(i: Int) = pad + w * i / (points.size - 1).toFloat()
            fun py(v: Double) = pad + h - (h * ((v - minY) / span)).toFloat()
            for (k in 0..2) {
                val y = pad + h * k / 2f
                drawLine(grid, Offset(pad, y), Offset(pad + w, y), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(i), py(p.y)) else path.lineTo(px(i), py(p.y)) }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            points.forEachIndexed { i, p -> drawCircle(color, 4.dp.toPx(), Offset(px(i), py(p.y))) }
        }
    }
}
