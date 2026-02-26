package com.mssh.terminal

/**
 * ANSI/VT100/xterm terminal emulator.
 *
 * Processes a byte stream and updates the terminal buffer accordingly.
 * Supports basic escape sequences including cursor movement, colors,
 * scrolling, and screen clearing.
 */
class TerminalEmulator(
    cols: Int = 80,
    rows: Int = 24,
    private val onOutput: ((ByteArray) -> Unit)? = null
) {
    val buffer = TerminalBuffer(cols, rows)
    val attrs = TerminalAttributes()

    var cursorCol: Int = 0
        private set
    var cursorRow: Int = 0
        private set
    var cursorVisible: Boolean = true
        private set

    // Scroll region
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    // Parser state
    private var state = ParseState.NORMAL
    private val escapeParams = StringBuilder()
    private var savedCursorCol = 0
    private var savedCursorRow = 0
    private var savedAttrs = attrs.copy()

    // Tab stops (default every 8 columns)
    private val tabStops = BooleanArray(cols) { it > 0 && it % 8 == 0 }

    // Title
    var title: String = ""
        private set

    // Redraw callback
    var onRedraw: (() -> Unit)? = null

    private var cols: Int = cols
    private var rows: Int = rows

    private enum class ParseState {
        NORMAL,
        ESCAPE,
        CSI,         // Control Sequence Introducer (ESC [)
        OSC,         // Operating System Command (ESC ])
        OSC_STRING,
        CHARSET      // Character set selection (ESC ( or ESC ))
    }

    /**
     * Process incoming bytes from the SSH stream.
     */
    fun processBytes(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        for (i in offset until offset + length) {
            processByte(data[i])
        }
        onRedraw?.invoke()
    }

    /**
     * Process a single byte.
     */
    private fun processByte(b: Byte) {
        val c = b.toInt() and 0xFF

        when (state) {
            ParseState.NORMAL -> processNormal(c)
            ParseState.ESCAPE -> processEscape(c)
            ParseState.CSI -> processCsi(c)
            ParseState.OSC -> processOsc(c)
            ParseState.OSC_STRING -> processOscString(c)
            ParseState.CHARSET -> {
                // Consume charset designation character and return to normal
                state = ParseState.NORMAL
            }
        }
    }

    private fun processNormal(c: Int) {
        when (c) {
            0x07 -> { /* Bell - ignore */ }
            0x08 -> { // Backspace
                if (cursorCol > 0) cursorCol--
            }
            0x09 -> { // Tab
                cursorCol = nextTabStop(cursorCol)
            }
            0x0A, 0x0B, 0x0C -> { // LF, VT, FF
                lineFeed()
            }
            0x0D -> { // CR
                cursorCol = 0
            }
            0x1B -> { // ESC
                state = ParseState.ESCAPE
                escapeParams.clear()
            }
            else -> {
                if (c >= 0x20) {
                    putChar(c.toChar())
                }
            }
        }
    }

    private fun processEscape(c: Int) {
        when (c) {
            '['.code -> {
                state = ParseState.CSI
                escapeParams.clear()
            }
            ']'.code -> {
                state = ParseState.OSC
                escapeParams.clear()
            }
            '('.code, ')'.code -> {
                state = ParseState.CHARSET
            }
            'D'.code -> { // Index (scroll up)
                lineFeed()
                state = ParseState.NORMAL
            }
            'M'.code -> { // Reverse Index (scroll down)
                reverseLineFeed()
                state = ParseState.NORMAL
            }
            'E'.code -> { // Next Line
                cursorCol = 0
                lineFeed()
                state = ParseState.NORMAL
            }
            '7'.code -> { // Save Cursor
                saveCursor()
                state = ParseState.NORMAL
            }
            '8'.code -> { // Restore Cursor
                restoreCursor()
                state = ParseState.NORMAL
            }
            'c'.code -> { // Full Reset
                fullReset()
                state = ParseState.NORMAL
            }
            '='.code, '>'.code -> { // Application/Normal keypad mode
                state = ParseState.NORMAL
            }
            else -> {
                state = ParseState.NORMAL
            }
        }
    }

    private fun processCsi(c: Int) {
        when {
            c in '0'.code..'9'.code || c == ';'.code || c == '?'.code || c == '!'.code || c == '"'.code || c == ' '.code -> {
                escapeParams.append(c.toChar())
            }
            else -> {
                executeCsi(c.toChar())
                state = ParseState.NORMAL
            }
        }
    }

    private fun processOsc(c: Int) {
        when (c) {
            ';'.code -> {
                state = ParseState.OSC_STRING
                escapeParams.clear()
            }
            else -> {
                escapeParams.append(c.toChar())
                if (c == 0x07 || c == 0x1B) {
                    state = ParseState.NORMAL
                }
            }
        }
    }

    private fun processOscString(c: Int) {
        when (c) {
            0x07 -> { // BEL terminates OSC
                title = escapeParams.toString()
                state = ParseState.NORMAL
            }
            0x1B -> { // ESC might start ST (ESC \)
                title = escapeParams.toString()
                state = ParseState.NORMAL
            }
            else -> {
                escapeParams.append(c.toChar())
            }
        }
    }

    private fun executeCsi(finalChar: Char) {
        val paramStr = escapeParams.toString().removePrefix("?")
        val isPrivate = escapeParams.startsWith("?")
        val params = parseParams(paramStr)

        when (finalChar) {
            'A' -> { // Cursor Up
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorRow = (cursorRow - n).coerceAtLeast(scrollTop)
            }
            'B' -> { // Cursor Down
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorRow = (cursorRow + n).coerceAtMost(scrollBottom)
            }
            'C' -> { // Cursor Forward
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorCol = (cursorCol + n).coerceAtMost(cols - 1)
            }
            'D' -> { // Cursor Back
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorCol = (cursorCol - n).coerceAtLeast(0)
            }
            'E' -> { // Cursor Next Line
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorRow = (cursorRow + n).coerceAtMost(scrollBottom)
                cursorCol = 0
            }
            'F' -> { // Cursor Previous Line
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorRow = (cursorRow - n).coerceAtLeast(scrollTop)
                cursorCol = 0
            }
            'G' -> { // Cursor Horizontal Absolute
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorCol = (n - 1).coerceIn(0, cols - 1)
            }
            'H', 'f' -> { // Cursor Position
                val row = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                val col = params.getOrElse(1) { 1 }.coerceAtLeast(1)
                cursorRow = (row - 1).coerceIn(0, rows - 1)
                cursorCol = (col - 1).coerceIn(0, cols - 1)
            }
            'J' -> { // Erase in Display
                when (params.getOrElse(0) { 0 }) {
                    0 -> buffer.clearFromCursor(cursorCol, cursorRow)
                    1 -> buffer.clearToCursor(cursorCol, cursorRow)
                    2, 3 -> buffer.clearAll()
                }
            }
            'K' -> { // Erase in Line
                when (params.getOrElse(0) { 0 }) {
                    0 -> buffer.clearLineFromCursor(cursorCol, cursorRow)
                    1 -> buffer.clearLineToCursor(cursorCol, cursorRow)
                    2 -> buffer.clearLine(cursorRow)
                }
            }
            'L' -> { // Insert Lines
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.insertLines(cursorRow, n, scrollBottom)
            }
            'M' -> { // Delete Lines
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.deleteLines(cursorRow, n, scrollBottom)
            }
            'P' -> { // Delete Characters
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                // Shift characters left
                for (i in cursorCol until cols - n) {
                    buffer.setCell(i, cursorRow, buffer.getCell(i + n, cursorRow))
                }
                for (i in (cols - n) until cols) {
                    buffer.setCell(i, cursorRow, TerminalCell())
                }
            }
            'X' -> { // Erase Characters
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.eraseChars(cursorCol, cursorRow, n)
            }
            'd' -> { // Cursor Vertical Absolute
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                cursorRow = (n - 1).coerceIn(0, rows - 1)
            }
            'm' -> { // Select Graphic Rendition
                processSgr(params)
            }
            'r' -> { // Set Scroll Region
                val top = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                val bottom = params.getOrElse(1) { rows }
                scrollTop = (top - 1).coerceIn(0, rows - 1)
                scrollBottom = (bottom - 1).coerceIn(0, rows - 1)
                cursorRow = 0
                cursorCol = 0
            }
            'h' -> { // Set Mode
                if (isPrivate) {
                    for (p in params) {
                        when (p) {
                            25 -> cursorVisible = true
                        }
                    }
                }
            }
            'l' -> { // Reset Mode
                if (isPrivate) {
                    for (p in params) {
                        when (p) {
                            25 -> cursorVisible = false
                        }
                    }
                }
            }
            'c' -> { // Send Device Attributes
                onOutput?.invoke("\u001b[?1;2c".toByteArray())
            }
            'n' -> { // Device Status Report
                when (params.getOrElse(0) { 0 }) {
                    6 -> { // Cursor Position Report
                        onOutput?.invoke("\u001b[${cursorRow + 1};${cursorCol + 1}R".toByteArray())
                    }
                }
            }
            'S' -> { // Scroll Up
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                repeat(n) { buffer.scrollUp(scrollTop, scrollBottom) }
            }
            'T' -> { // Scroll Down
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                repeat(n) { buffer.scrollDown(scrollTop, scrollBottom) }
            }
            '@' -> { // Insert Blank Characters
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                // Shift characters right
                for (i in cols - 1 downTo cursorCol + n) {
                    buffer.setCell(i, cursorRow, buffer.getCell(i - n, cursorRow))
                }
                for (i in cursorCol until (cursorCol + n).coerceAtMost(cols)) {
                    buffer.setCell(i, cursorRow, TerminalCell())
                }
            }
        }
    }

    private fun processSgr(params: List<Int>) {
        if (params.isEmpty()) {
            attrs.reset()
            return
        }

        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> attrs.reset()
                1 -> attrs.bold = true
                3 -> attrs.italic = true
                4 -> attrs.underline = true
                7 -> attrs.inverse = true
                22 -> attrs.bold = false
                23 -> attrs.italic = false
                24 -> attrs.underline = false
                27 -> attrs.inverse = false
                // Standard foreground colors
                in 30..37 -> attrs.foreground = ANSI_COLORS[p - 30]
                39 -> attrs.foreground = TerminalCell.DEFAULT_FG
                // Standard background colors
                in 40..47 -> attrs.background = ANSI_COLORS[p - 40]
                49 -> attrs.background = TerminalCell.DEFAULT_BG
                // Bright foreground colors
                in 90..97 -> attrs.foreground = ANSI_BRIGHT_COLORS[p - 90]
                // Bright background colors
                in 100..107 -> attrs.background = ANSI_BRIGHT_COLORS[p - 100]
                // 256-color and true-color
                38 -> {
                    i++
                    if (i < params.size) {
                        when (params[i]) {
                            5 -> { // 256-color
                                i++
                                if (i < params.size) {
                                    attrs.foreground = get256Color(params[i])
                                }
                            }
                            2 -> { // True color
                                if (i + 3 < params.size) {
                                    val r = params[i + 1].coerceIn(0, 255)
                                    val g = params[i + 2].coerceIn(0, 255)
                                    val b = params[i + 3].coerceIn(0, 255)
                                    attrs.foreground = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    i += 3
                                }
                            }
                        }
                    }
                }
                48 -> {
                    i++
                    if (i < params.size) {
                        when (params[i]) {
                            5 -> { // 256-color
                                i++
                                if (i < params.size) {
                                    attrs.background = get256Color(params[i])
                                }
                            }
                            2 -> { // True color
                                if (i + 3 < params.size) {
                                    val r = params[i + 1].coerceIn(0, 255)
                                    val g = params[i + 2].coerceIn(0, 255)
                                    val b = params[i + 3].coerceIn(0, 255)
                                    attrs.background = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    i += 3
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
    }

    private fun putChar(char: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        buffer.setChar(cursorCol, cursorRow, char, attrs)
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            buffer.scrollUp(scrollTop, scrollBottom)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    private fun reverseLineFeed() {
        if (cursorRow == scrollTop) {
            buffer.scrollDown(scrollTop, scrollBottom)
        } else if (cursorRow > 0) {
            cursorRow--
        }
    }

    private fun saveCursor() {
        savedCursorCol = cursorCol
        savedCursorRow = cursorRow
        savedAttrs = attrs.copy()
    }

    private fun restoreCursor() {
        cursorCol = savedCursorCol
        cursorRow = savedCursorRow
        attrs.foreground = savedAttrs.foreground
        attrs.background = savedAttrs.background
        attrs.bold = savedAttrs.bold
        attrs.italic = savedAttrs.italic
        attrs.underline = savedAttrs.underline
        attrs.inverse = savedAttrs.inverse
    }

    private fun fullReset() {
        cursorCol = 0
        cursorRow = 0
        cursorVisible = true
        scrollTop = 0
        scrollBottom = rows - 1
        attrs.reset()
        buffer.clearAll()
    }

    private fun nextTabStop(currentCol: Int): Int {
        for (i in (currentCol + 1) until cols) {
            if (tabStops[i]) return i
        }
        return cols - 1
    }

    /**
     * Resize the terminal.
     */
    fun resize(newCols: Int, newRows: Int) {
        cols = newCols
        rows = newRows
        buffer.resize(newCols, newRows)
        scrollTop = 0
        scrollBottom = newRows - 1
        cursorCol = cursorCol.coerceAtMost(newCols - 1)
        cursorRow = cursorRow.coerceAtMost(newRows - 1)
    }

    val currentCols: Int get() = cols
    val currentRows: Int get() = rows

    private fun parseParams(str: String): List<Int> {
        if (str.isBlank()) return emptyList()
        return str.split(';').map { it.toIntOrNull() ?: 0 }
    }

    companion object {
        // Standard 8 ANSI colors
        val ANSI_COLORS = intArrayOf(
            0xFF000000.toInt(), // Black
            0xFFCC0000.toInt(), // Red
            0xFF00CC00.toInt(), // Green
            0xFFCCCC00.toInt(), // Yellow
            0xFF0000CC.toInt(), // Blue
            0xFFCC00CC.toInt(), // Magenta
            0xFF00CCCC.toInt(), // Cyan
            0xFFCCCCCC.toInt()  // White
        )

        // Bright ANSI colors
        val ANSI_BRIGHT_COLORS = intArrayOf(
            0xFF555555.toInt(), // Bright Black (Gray)
            0xFFFF5555.toInt(), // Bright Red
            0xFF55FF55.toInt(), // Bright Green
            0xFFFFFF55.toInt(), // Bright Yellow
            0xFF5555FF.toInt(), // Bright Blue
            0xFFFF55FF.toInt(), // Bright Magenta
            0xFF55FFFF.toInt(), // Bright Cyan
            0xFFFFFFFF.toInt()  // Bright White
        )

        /**
         * Convert a 256-color index to an ARGB color.
         */
        fun get256Color(index: Int): Int {
            return when {
                index < 8 -> ANSI_COLORS[index]
                index < 16 -> ANSI_BRIGHT_COLORS[index - 8]
                index < 232 -> {
                    // 216-color cube: 16-231
                    val i = index - 16
                    val r = (i / 36) * 51
                    val g = ((i / 6) % 6) * 51
                    val b = (i % 6) * 51
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                else -> {
                    // Grayscale: 232-255
                    val gray = 8 + (index - 232) * 10
                    (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
            }
        }
    }
}
