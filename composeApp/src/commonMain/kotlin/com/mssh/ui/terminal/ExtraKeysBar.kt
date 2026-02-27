package com.mssh.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BarBg = Color(0xFF1A1A2E)
private val KeyBg = Color(0xFF2A2A40)
private val KeyBgActive = Color(0xFF4A9EFF)
private val KeyText = Color(0xFFBBBBCC)
private val KeyTextActive = Color.White
private val DividerColor = Color(0xFF333350)

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BarBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // -- Modifier keys --
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

            KeyDivider()

            // -- Common special keys --
            ExtraKeyButton(label = "ESC") { onInput(byteArrayOf(0x1B)) }
            ExtraKeyButton(label = "TAB") { onInput(byteArrayOf(0x09)) }

            KeyDivider()

            // -- Arrow keys --
            ExtraKeyButton(label = "\u25C0") { onInput("\u001b[D".toByteArray()) }
            ExtraKeyButton(label = "\u25B2") { onInput("\u001b[A".toByteArray()) }
            ExtraKeyButton(label = "\u25BC") { onInput("\u001b[B".toByteArray()) }
            ExtraKeyButton(label = "\u25B6") { onInput("\u001b[C".toByteArray()) }

            KeyDivider()

            // -- Symbols --
            ExtraKeyButton(label = "|") {
                val bytes = if (ctrlActive) byteArrayOf(0x1C) else "|".toByteArray()
                onInput(bytes)
                ctrlActive = false
            }
            ExtraKeyButton(label = "/") { onInput("/".toByteArray()) }
            ExtraKeyButton(label = "-") { onInput("-".toByteArray()) }
            ExtraKeyButton(label = "~") { onInput("~".toByteArray()) }
            ExtraKeyButton(label = "_") { onInput("_".toByteArray()) }
            ExtraKeyButton(label = "=") { onInput("=".toByteArray()) }

            KeyDivider()

            // -- Brackets --
            ExtraKeyButton(label = "{") { onInput("{".toByteArray()) }
            ExtraKeyButton(label = "}") { onInput("}".toByteArray()) }
            ExtraKeyButton(label = "[") { onInput("[".toByteArray()) }
            ExtraKeyButton(label = "]") { onInput("]".toByteArray()) }

            KeyDivider()

            // -- Navigation --
            ExtraKeyButton(label = "Home") { onInput("\u001b[H".toByteArray()) }
            ExtraKeyButton(label = "End") { onInput("\u001b[F".toByteArray()) }
            ExtraKeyButton(label = "PgUp") { onInput("\u001b[5~".toByteArray()) }
            ExtraKeyButton(label = "PgDn") { onInput("\u001b[6~".toByteArray()) }
        }

        // Left fade edge
        if (scrollState.value > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(BarBg, Color.Transparent)
                        )
                    )
            )
        }

        // Right fade edge
        if (scrollState.value < scrollState.maxValue) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, BarBg)
                        )
                    )
            )
        }
    }
}

@Composable
private fun KeyDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(DividerColor)
    )
    Spacer(modifier = Modifier.width(3.dp))
}

@Composable
private fun ExtraKeyButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) KeyBgActive else KeyBg
    val textColor = if (isActive) KeyTextActive else KeyText

    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
