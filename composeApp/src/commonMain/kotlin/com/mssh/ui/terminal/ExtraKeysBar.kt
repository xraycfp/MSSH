package com.mssh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ExtraKey(
    val label: String,
    val bytes: ByteArray,
    val isToggle: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtraKey) return false
        return label == other.label
    }
    override fun hashCode() = label.hashCode()
}

/**
 * Extra keys toolbar for mobile SSH terminal.
 * Provides quick access to Ctrl, Alt, Esc, Tab, arrows, etc.
 */
@Composable
fun ExtraKeysBar(
    onInput: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D3F))
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle keys
        ExtraKeyButton(
            label = "Ctrl",
            isActive = ctrlActive,
            onClick = { ctrlActive = !ctrlActive }
        )
        ExtraKeyButton(
            label = "Alt",
            isActive = altActive,
            onClick = { altActive = !altActive }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Function keys
        ExtraKeyButton(label = "ESC") { onInput(byteArrayOf(0x1B)) }
        ExtraKeyButton(label = "ENTER") { onInput(byteArrayOf(0x0D)) }
        ExtraKeyButton(label = "BS") { onInput(byteArrayOf(0x7F)) }
        ExtraKeyButton(label = "TAB") { onInput(byteArrayOf(0x09)) }
        ExtraKeyButton(label = "|") {
            val bytes = if (ctrlActive) byteArrayOf(0x1C) else "|".toByteArray()
            onInput(bytes)
            ctrlActive = false
        }
        ExtraKeyButton(label = "/") { onInput("/".toByteArray()) }
        ExtraKeyButton(label = "-") { onInput("-".toByteArray()) }
        ExtraKeyButton(label = "~") { onInput("~".toByteArray()) }

        Spacer(modifier = Modifier.width(4.dp))

        // Arrow keys
        ExtraKeyButton(label = "▲") { onInput("\u001b[A".toByteArray()) }
        ExtraKeyButton(label = "▼") { onInput("\u001b[B".toByteArray()) }
        ExtraKeyButton(label = "◀") { onInput("\u001b[D".toByteArray()) }
        ExtraKeyButton(label = "▶") { onInput("\u001b[C".toByteArray()) }

        Spacer(modifier = Modifier.width(4.dp))

        // Common control sequences
        ExtraKeyButton(label = "Home") { onInput("\u001b[H".toByteArray()) }
        ExtraKeyButton(label = "End") { onInput("\u001b[F".toByteArray()) }
        ExtraKeyButton(label = "PgUp") { onInput("\u001b[5~".toByteArray()) }
        ExtraKeyButton(label = "PgDn") { onInput("\u001b[6~".toByteArray()) }
    }
}

@Composable
private fun ExtraKeyButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFF4A9EFF) else Color(0xFF3D3D52)
    val textColor = if (isActive) Color.White else Color(0xFFCCCCCC)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
