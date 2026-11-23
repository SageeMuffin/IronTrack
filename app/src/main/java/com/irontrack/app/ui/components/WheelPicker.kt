package com.irontrack.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irontrack.app.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Sélecteur à molette : on fait défiler les valeurs au lieu d'ouvrir le clavier.
 * L'élément centré est la valeur sélectionnée.
 */
@Composable
fun WheelPicker(
    labels: List<String>,
    initialIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: Int = 5,
    itemHeight: Dp = 44.dp,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val pad = visibleItems / 2
    val onChange by rememberUpdatedState(onIndexChange)
    val centered by remember { derivedStateOf { centerIndex(state) } }

    LaunchedEffect(state) {
        snapshotFlow { centerIndex(state) }.distinctUntilChanged().collect { onChange(it) }
    }

    Box(modifier.height(itemHeight * visibleItems), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(itemHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(labels.size) { i ->
                val selected = i == centered
                Box(
                    Modifier.fillMaxWidth().height(itemHeight).clickable { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels[i],
                        style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun centerIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val items = info.visibleItemsInfo
    if (items.isEmpty()) return state.firstVisibleItemIndex
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return items.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: state.firstVisibleItemIndex
}

/** Une colonne de molette dans une feuille de saisie. */
data class WheelSpec(
    val labels: List<String>,
    val initialIndex: Int,
    val caption: String? = null,
    val weight: Float = 1f,
)

/**
 * Feuille de saisie par molettes. [onClear] permet de revenir à « Non renseigné ».
 * [footer] reçoit les indices courants (ex. pour expliquer la valeur de RIR choisie).
 */
@Composable
fun WheelSheet(
    title: String,
    wheels: List<WheelSpec>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
    onClear: (() -> Unit)? = null,
    separator: String? = null,
    footer: (@Composable (List<Int>) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val indices = remember { mutableStateListOf<Int>().apply { addAll(wheels.map { it.initialIndex }) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wheels.forEachIndexed { i, w ->
                    Column(Modifier.weight(w.weight), horizontalAlignment = Alignment.CenterHorizontally) {
                        w.caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        WheelPicker(labels = w.labels, initialIndex = w.initialIndex, onIndexChange = { indices[i] = it })
                    }
                    if (separator != null && i < wheels.lastIndex) {
                        Text(separator, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            footer?.let {
                Spacer(Modifier.height(8.dp))
                it(indices.toList())
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onClear != null) {
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_clear_value)) }
                } else {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                }
                Button(onClick = { onConfirm(indices.toList()) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_validate)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
