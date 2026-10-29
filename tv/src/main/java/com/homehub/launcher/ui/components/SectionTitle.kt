package com.homehub.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.homehub.launcher.ui.theme.HubColors

/** Libellé de section : petites capitales espacées, en retrait visuel. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = HubColors.TextTertiary,
        maxLines = 1,
        modifier = modifier,
    )
}
