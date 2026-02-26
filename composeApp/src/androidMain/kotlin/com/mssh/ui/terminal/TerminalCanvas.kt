package com.mssh.ui.terminal

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.mssh.terminal.TerminalCell
import com.mssh.terminal.TerminalEmulator
import kotlinx.coroutines.launch

/**
 * Compose terminal renderer + input bridge.
 *
 * Rendering uses a plain-text snapshot to ensure visibility on all devices.
 */
@Composable
fun TerminalCanvas(
    emulator: TerminalEmulator,
    onInput: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Float = 14f,
    onSizeChanged: ((cols: Int, rows: Int) -> Unit)? = null
) {
    val tag = remember(emulator) { "TerminalCanvas@${emulator.hashCode()}" }
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val redrawScope = rememberCoroutineScope()
    var imeBuffer by remember { mutableStateOf(TextFieldValue("")) }

    // Trigger recomposition on terminal updates
    var redrawTick by remember { mutableIntStateOf(0) }

    DisposableEffect(emulator) {
        emulator.onRedraw = {
            redrawScope.launch {
                redrawTick++
            }
        }
        onDispose {
            emulator.onRedraw = null
        }
    }

    LaunchedEffect(redrawTick) {
        if (redrawTick <= 10 || redrawTick % 100 == 0) {
            val cols = emulator.currentCols
            val rows = emulator.currentRows
            val col = emulator.cursorCol.coerceIn(0, (cols - 1).coerceAtLeast(0))
            val row = emulator.cursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0))
            val prevCol = (col - 1).coerceAtLeast(0)
            val prevCell = emulator.buffer.getCell(prevCol, row)
            Log.d(
                tag,
                "redraw tick=$redrawTick cursor=${col},${row} prevCell='${prevCell.char}'(${prevCell.char.code}) size=${cols}x${rows}"
            )
        }
    }

    val cellWidth = fontSize * 0.6f * density.density
    val cellHeight = fontSize * 1.2f * density.density

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(TerminalCell.DEFAULT_BG))
            .onSizeChanged { size ->
                if (cellWidth > 0 && cellHeight > 0) {
                    val newCols = (size.width / cellWidth).toInt().coerceAtLeast(1)
                    val newRows = (size.height / cellHeight).toInt().coerceAtLeast(1)
                    if (newCols != emulator.currentCols || newRows != emulator.currentRows) {
                        Log.d(
                            tag,
                            "onSizeChanged px=${size.width}x${size.height} -> colsRows=${newCols}x${newRows} old=${emulator.currentCols}x${emulator.currentRows}"
                        )
                        emulator.resize(newCols, newRows)
                        onSizeChanged?.invoke(newCols, newRows)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    Log.d(tag, "pointer tap -> requestFocus")
                    focusRequester.requestFocus()
                }
            }
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = redrawTick

        val textSnapshot = remember(redrawTick, emulator.currentCols, emulator.currentRows) {
            buildTerminalSnapshot(emulator)
        }

        BasicText(
            text = textSnapshot,
            modifier = Modifier.fillMaxSize(),
            style = TextStyle(
                color = Color(TerminalCell.DEFAULT_FG),
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.2f).sp
            )
        )

        // Transparent full-screen IME bridge for Android soft keyboard input.
        BasicTextField(
            value = imeBuffer,
            onValueChange = { newValue ->
                val delta = emitImeDelta(imeBuffer.text, newValue.text, onInput)
                if (delta.deleted > 0 || delta.insertedChars > 0) {
                    Log.d(
                        tag,
                        "ime delta deleted=${delta.deleted} insertedChars=${delta.insertedChars} sentBytes=${delta.insertedBytes} bytesPreview=${previewBytes(delta.insertedBytesRaw)}"
                    )
                }

                var text = newValue.text
                if (text.length > IME_BUFFER_MAX) {
                    Log.d(tag, "ime buffer trimmed from=${text.length} to=$IME_BUFFER_TRIM_TO")
                    text = text.takeLast(IME_BUFFER_TRIM_TO)
                }
                imeBuffer = TextFieldValue(
                    text = text,
                    selection = TextRange(text.length)
                )
            },
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Default
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    Log.d(tag, "keyboard action onDone -> send CR")
                    onInput(byteArrayOf(0x0D))
                },
                onGo = {
                    Log.d(tag, "keyboard action onGo -> send CR")
                    onInput(byteArrayOf(0x0D))
                },
                onSearch = {
                    Log.d(tag, "keyboard action onSearch -> send CR")
                    onInput(byteArrayOf(0x0D))
                },
                onSend = {
                    Log.d(tag, "keyboard action onSend -> send CR")
                    onInput(byteArrayOf(0x0D))
                }
            ),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    Log.d(
                        tag,
                        "focus changed isFocused=${state.isFocused} hasFocus=${state.hasFocus}"
                    )
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val bytes = keyEventToBytes(event)
                        Log.d(
                            tag,
                            "key down key=${event.key} codePoint=${event.utf16CodePoint} ctrl=${event.isCtrlPressed} alt=${event.isAltPressed} mapped=${bytes != null}${if (bytes != null) " bytes=${bytes.size}" else ""}"
                        )
                        if (bytes != null) {
                            onInput(bytes)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
        )
    }
}

private const val IME_BUFFER_MAX = 256
private const val IME_BUFFER_TRIM_TO = 128

private data class ImeDeltaStats(
    val deleted: Int,
    val insertedChars: Int,
    val insertedBytes: Int,
    val insertedBytesRaw: ByteArray
)

private fun emitImeDelta(
    previous: String,
    current: String,
    onInput: (ByteArray) -> Unit
): ImeDeltaStats {
    if (previous == current) {
        return ImeDeltaStats(
            deleted = 0,
            insertedChars = 0,
            insertedBytes = 0,
            insertedBytesRaw = ByteArray(0)
        )
    }

    val prefixLen = previous.commonPrefixWith(current).length
    val previousRemainder = previous.substring(prefixLen)
    val currentRemainder = current.substring(prefixLen)
    val suffixLen = previousRemainder.commonSuffixWith(currentRemainder).length

    val deleted = previousRemainder.length - suffixLen
    repeat(deleted.coerceAtLeast(0)) {
        onInput(byteArrayOf(0x7F))
    }

    val inserted = currentRemainder.substring(0, currentRemainder.length - suffixLen)
    val insertedBytes = if (inserted.isNotEmpty()) {
        inserted
            .replace("\r\n", "\r")
            .replace('\n', '\r')
            .toByteArray(Charsets.UTF_8)
    } else {
        ByteArray(0)
    }
    if (insertedBytes.isNotEmpty()) {
        onInput(insertedBytes)
    }

    return ImeDeltaStats(
        deleted = deleted.coerceAtLeast(0),
        insertedChars = inserted.length,
        insertedBytes = insertedBytes.size,
        insertedBytesRaw = insertedBytes
    )
}

private fun previewBytes(bytes: ByteArray, max: Int = 16): String {
    if (bytes.isEmpty()) return "<empty>"
    val take = minOf(bytes.size, max)
    val head = (0 until take).joinToString(" ") { i ->
        String.format("%02x", bytes[i].toInt() and 0xFF)
    }
    return if (bytes.size > max) "$head ..." else head
}

private fun buildTerminalSnapshot(emulator: TerminalEmulator): String {
    val buffer = emulator.buffer
    val cols = emulator.currentCols
    val rows = emulator.currentRows

    if (cols <= 0 || rows <= 0) return ""

    val cursorCol = emulator.cursorCol.coerceIn(0, cols - 1)
    val cursorRow = emulator.cursorRow.coerceIn(0, rows - 1)
    val sb = StringBuilder(rows * (cols + 1))

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val cell = buffer.getCell(col, row)
            val normalized = if (cell.char == '\u0000') ' ' else cell.char
            val charToDraw = if (
                emulator.cursorVisible &&
                row == cursorRow &&
                col == cursorCol &&
                normalized == ' '
            ) {
                '_'
            } else {
                normalized
            }
            sb.append(charToDraw)
        }
        if (row < rows - 1) {
            sb.append('\n')
        }
    }
    return sb.toString()
}

/**
 * Convert a Compose key event to bytes to send to the terminal.
 */
private fun keyEventToBytes(event: KeyEvent): ByteArray? {
    val char = event.utf16CodePoint
    if (char > 0 && event.key != Key.Enter && event.key != Key.Backspace
        && event.key != Key.Delete && event.key != Key.Tab && event.key != Key.Escape
    ) {
        if (event.isCtrlPressed && char in 0x40..0x7F) {
            return byteArrayOf((char and 0x1F).toByte())
        }
        if (char < 0x80) {
            return byteArrayOf(char.toByte())
        }
        return Char(char).toString().toByteArray(Charsets.UTF_8)
    }

    return when (event.key) {
        Key.Enter -> byteArrayOf(0x0D)
        Key.Backspace -> byteArrayOf(0x7F)
        Key.Delete -> "\u001b[3~".toByteArray()
        Key.Tab -> byteArrayOf(0x09)
        Key.Escape -> byteArrayOf(0x1B)
        Key.DirectionUp -> "\u001b[A".toByteArray()
        Key.DirectionDown -> "\u001b[B".toByteArray()
        Key.DirectionRight -> "\u001b[C".toByteArray()
        Key.DirectionLeft -> "\u001b[D".toByteArray()
        Key.MoveHome -> "\u001b[H".toByteArray()
        Key.MoveEnd -> "\u001b[F".toByteArray()
        Key.PageUp -> "\u001b[5~".toByteArray()
        Key.PageDown -> "\u001b[6~".toByteArray()
        Key.Insert -> "\u001b[2~".toByteArray()
        Key.F1 -> "\u001bOP".toByteArray()
        Key.F2 -> "\u001bOQ".toByteArray()
        Key.F3 -> "\u001bOR".toByteArray()
        Key.F4 -> "\u001bOS".toByteArray()
        Key.F5 -> "\u001b[15~".toByteArray()
        Key.F6 -> "\u001b[17~".toByteArray()
        Key.F7 -> "\u001b[18~".toByteArray()
        Key.F8 -> "\u001b[19~".toByteArray()
        Key.F9 -> "\u001b[20~".toByteArray()
        Key.F10 -> "\u001b[21~".toByteArray()
        Key.F11 -> "\u001b[23~".toByteArray()
        Key.F12 -> "\u001b[24~".toByteArray()
        else -> null
    }
}
