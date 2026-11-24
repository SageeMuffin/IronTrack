package com.irontrack.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Texte sur une ligne qui réduit sa taille jusqu'à tenir entièrement dans la largeur disponible
 * (ex. « Performances » dans la barre de navigation sur les écrans étroits), sans jamais être coupé.
 */
@Composable
fun AutoShrinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    minFontSize: TextUnit = 8.sp,
) {
    val base = if (style.fontSize == TextUnit.Unspecified) 12.sp else style.fontSize
    var fontSize by remember(text, base) { mutableStateOf(base) }
    var ready by remember(text, base) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(fontSize = fontSize, letterSpacing = if (fontSize < base) 0.sp else style.letterSpacing),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        textAlign = TextAlign.Center,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize.value > minFontSize.value) {
                fontSize = (fontSize.value * 0.92f).coerceAtLeast(minFontSize.value).sp
            } else {
                ready = true
            }
        },
    )
}
