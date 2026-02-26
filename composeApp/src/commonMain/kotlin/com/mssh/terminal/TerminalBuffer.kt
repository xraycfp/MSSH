package com.mssh.terminal

/**
 * Represents a single character cell in the terminal buffer.
 */
data class TerminalCell(
    val char: Char = ' ',
    val foreground: Int = DEFAULT_FG,
    val background: Int = DEFAULT_BG,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false
) {
    companion object {
        const val DEFAULT_FG = 0xFFCCCCCC.toInt()
        const val DEFAULT_BG = 0xFF1E1E2E.toInt()
    }
}

/**
 * Terminal screen buffer.
 */
class TerminalBuffer(
    var cols: Int,
    var rows: Int,
    private val maxScrollback: Int = 5000
) {
    // Current visible screen
    private var screen: Array<Array<TerminalCell>> = createEmptyScreen(cols, rows)

    // Scrollback buffer (lines that scrolled off the top)
    private val scrollback: MutableList<Array<TerminalCell>> = mutableListOf()

    /**
     * Get a cell at the given position.
     */
    fun getCell(col: Int, row: Int): TerminalCell {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return TerminalCell()
        return screen[row][col]
    }

    /**
     * Set a cell at the given position.
     */
    fun setCell(col: Int, row: Int, cell: TerminalCell) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return
        screen[row][col] = cell
    }

    /**
     * Set a character with current attributes at position.
     */
    fun setChar(col: Int, row: Int, char: Char, attrs: TerminalAttributes) {
        setCell(col, row, TerminalCell(
            char = char,
            foreground = if (attrs.inverse) attrs.background else attrs.foreground,
            background = if (attrs.inverse) attrs.foreground else attrs.background,
            bold = attrs.bold,
            italic = attrs.italic,
            underline = attrs.underline,
            inverse = false
        ))
    }

    /**
     * Scroll the screen up by one line within the given scroll region.
     */
    fun scrollUp(topRow: Int = 0, bottomRow: Int = rows - 1) {
        if (topRow == 0) {
            // Save the top line to scrollback
            scrollback.add(screen[0].copyOf())
            if (scrollback.size > maxScrollback) {
                scrollback.removeAt(0)
            }
        }
        for (row in topRow until bottomRow) {
            screen[row] = screen[row + 1]
        }
        screen[bottomRow] = Array(cols) { TerminalCell() }
    }

    /**
     * Scroll the screen down by one line within the given scroll region.
     */
    fun scrollDown(topRow: Int = 0, bottomRow: Int = rows - 1) {
        for (row in bottomRow downTo topRow + 1) {
            screen[row] = screen[row - 1]
        }
        screen[topRow] = Array(cols) { TerminalCell() }
    }

    /**
     * Clear the entire screen.
     */
    fun clearAll() {
        screen = createEmptyScreen(cols, rows)
    }

    /**
     * Clear from cursor to end of screen.
     */
    fun clearFromCursor(cursorCol: Int, cursorRow: Int) {
        // Clear rest of current line
        for (col in cursorCol until cols) {
            screen[cursorRow][col] = TerminalCell()
        }
        // Clear all lines below
        for (row in (cursorRow + 1) until rows) {
            screen[row] = Array(cols) { TerminalCell() }
        }
    }

    /**
     * Clear from start of screen to cursor.
     */
    fun clearToCursor(cursorCol: Int, cursorRow: Int) {
        for (row in 0 until cursorRow) {
            screen[row] = Array(cols) { TerminalCell() }
        }
        for (col in 0..cursorCol.coerceAtMost(cols - 1)) {
            screen[cursorRow][col] = TerminalCell()
        }
    }

    /**
     * Clear line at row.
     */
    fun clearLine(row: Int) {
        if (row in 0 until rows) {
            screen[row] = Array(cols) { TerminalCell() }
        }
    }

    /**
     * Clear from cursor to end of line.
     */
    fun clearLineFromCursor(col: Int, row: Int) {
        if (row in 0 until rows) {
            for (c in col until cols) {
                screen[row][c] = TerminalCell()
            }
        }
    }

    /**
     * Clear from start of line to cursor.
     */
    fun clearLineToCursor(col: Int, row: Int) {
        if (row in 0 until rows) {
            for (c in 0..col.coerceAtMost(cols - 1)) {
                screen[row][c] = TerminalCell()
            }
        }
    }

    /**
     * Erase characters at position.
     */
    fun eraseChars(col: Int, row: Int, count: Int) {
        if (row in 0 until rows) {
            for (i in 0 until count) {
                val c = col + i
                if (c < cols) {
                    screen[row][c] = TerminalCell()
                }
            }
        }
    }

    /**
     * Insert blank lines at row, pushing content down.
     */
    fun insertLines(row: Int, count: Int, bottomRow: Int) {
        for (i in 0 until count) {
            for (r in bottomRow downTo row + 1) {
                screen[r] = screen[r - 1]
            }
            screen[row] = Array(cols) { TerminalCell() }
        }
    }

    /**
     * Delete lines at row, pulling content up.
     */
    fun deleteLines(row: Int, count: Int, bottomRow: Int) {
        for (i in 0 until count) {
            for (r in row until bottomRow) {
                screen[r] = screen[r + 1]
            }
            screen[bottomRow] = Array(cols) { TerminalCell() }
        }
    }

    /**
     * Resize the buffer.
     */
    fun resize(newCols: Int, newRows: Int) {
        val newScreen = createEmptyScreen(newCols, newRows)
        val copyRows = minOf(rows, newRows)
        val copyCols = minOf(cols, newCols)
        for (row in 0 until copyRows) {
            for (col in 0 until copyCols) {
                newScreen[row][col] = screen[row][col]
            }
        }
        screen = newScreen
        cols = newCols
        rows = newRows
    }

    /**
     * Get scrollback line count.
     */
    fun scrollbackSize(): Int = scrollback.size

    /**
     * Get a scrollback line (0 = most recent).
     */
    fun getScrollbackLine(index: Int): Array<TerminalCell>? {
        val actualIndex = scrollback.size - 1 - index
        return if (actualIndex in scrollback.indices) scrollback[actualIndex] else null
    }

    private fun createEmptyScreen(c: Int, r: Int): Array<Array<TerminalCell>> {
        return Array(r) { Array(c) { TerminalCell() } }
    }
}

/**
 * Current text attributes for new characters.
 */
data class TerminalAttributes(
    var foreground: Int = TerminalCell.DEFAULT_FG,
    var background: Int = TerminalCell.DEFAULT_BG,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var inverse: Boolean = false
) {
    fun reset() {
        foreground = TerminalCell.DEFAULT_FG
        background = TerminalCell.DEFAULT_BG
        bold = false
        italic = false
        underline = false
        inverse = false
    }

    fun copy(): TerminalAttributes = TerminalAttributes(
        foreground, background, bold, italic, underline, inverse
    )
}
