package io.github.figs0203.afinador.songbook

import io.github.figs0203.afinador.music.Chords

/** One rendered line of a chord sheet, flagged when it only holds chords. */
data class SheetLine(val text: String, val isChordLine: Boolean)

/**
 * A run of lines that must be laid out together. Tablature blocks keep their
 * columns, so they are shown without wrapping; everything else wraps freely.
 */
data class SheetBlock(val lines: List<SheetLine>, val isTab: Boolean) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * Turn a plain-text tab (Ultimate Guitar, CifraClub or pasted by hand) into
 * displayable lines, transposing chord lines only.
 *
 * Chord lines are recognised by content, never by position, because both sites
 * mix chord lines, lyric lines and ASCII tablature in the same `<pre>` block.
 * When a chord grows or shrinks the original column of the next chord is kept,
 * so the chord still sits over the syllable it belongs to in a monospace view.
 */
object ChordSheet {

    private val SEPARATORS = setOf("|", "-", "–", "—", ":", "%", "x2", "2x", "(x2)", "(2x)")
    private val NOT_CHORD_LABELS = listOf("tuning", "afinaci", "capo", "capotraste", "standard")

    fun render(text: String, semitones: Int): List<SheetLine> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        return normalized.split('\n').map { line ->
            val chordLine = isChordLine(line)
            SheetLine(if (chordLine) transposeLine(line, semitones) else line, chordLine)
        }
    }

    /**
     * Groups lines so the view can wrap prose while keeping tablature intact.
     * Blank lines between two tab lines stay inside the block, because the same
     * tab is often published as two stacked halves separated by an empty line.
     */
    fun blocks(lines: List<SheetLine>): List<SheetBlock> {
        val result = mutableListOf<SheetBlock>()
        var index = 0
        while (index < lines.size) {
            if (!isTabLine(lines[index].text)) {
                val start = index
                while (index < lines.size && !isTabLine(lines[index].text)) index++
                result.add(SheetBlock(lines.subList(start, index), isTab = false))
                continue
            }

            val start = index
            var end = index + 1
            var probe = index + 1
            while (probe < lines.size) {
                val line = lines[probe]
                if (isTabLine(line.text)) {
                    end = probe + 1
                    probe++
                    continue
                }
                if (line.text.isBlank()) {
                    var next = probe
                    while (next < lines.size && lines[next].text.isBlank()) next++
                    if (next < lines.size && isTabLine(lines[next].text)) {
                        probe = next
                        continue
                    }
                }
                break
            }
            result.add(SheetBlock(lines.subList(start, end), isTab = true))
            index = end
        }
        return result
    }

    /**
     * Breaks the sheet into lines that already fit [columns] characters, so a
     * wrapped lyric keeps its chords above the syllables they belong to.
     *
     * A chord line and the lyric under it are cut at the same column; wrapping
     * them separately (as a plain text view does) leaves a chord row over the
     * wrong row of text whenever one of them is wider than the screen.
     */
    fun wrap(lines: List<SheetLine>, columns: Int): List<SheetLine> {
        if (columns < 8) return lines
        val result = mutableListOf<SheetLine>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val next = lines.getOrNull(index + 1)
            when {
                line.isChordLine && next != null && !next.isChordLine && next.text.isNotBlank() -> {
                    wrapPair(line.text, next.text, columns, result)
                    index += 2
                }

                line.isChordLine -> {
                    wrapChordLine(line.text, columns).forEach { result.add(SheetLine(it, true)) }
                    index++
                }

                else -> {
                    wrapText(line.text, columns).forEach { result.add(SheetLine(it, false)) }
                    index++
                }
            }
        }
        return result
    }

    /** Cuts a chord line and its lyric in matching pieces, one pair per row. */
    private fun wrapPair(
        chord: String,
        lyric: String,
        columns: Int,
        result: MutableList<SheetLine>
    ) {
        var chordRest = chord
        var lyricRest = lyric

        while (lyricRest.length > columns) {
            val cut = cutFor(chordRest, lyricRest, columns)
            addRow(result, chordRest.take(cut), lyricRest.take(cut))
            chordRest = chordRest.drop(cut)
            lyricRest = lyricRest.drop(cut)
            // Both sides lose the same indentation, so the columns stay aligned.
            val shift = minOf(chordRest.indentWidth(), lyricRest.indentWidth())
            chordRest = chordRest.drop(shift)
            lyricRest = lyricRest.drop(shift)
        }

        if (chordRest.isNotBlank()) {
            wrapChordLine(chordRest, columns).forEach { result.add(SheetLine(it, true)) }
        }
        if (lyricRest.isNotEmpty() || result.isEmpty()) {
            result.add(SheetLine(lyricRest, false))
        }
    }

    /** Adds a row, skipping chord rows that only hold spaces left behind by the cut. */
    private fun addRow(result: MutableList<SheetLine>, chord: String, lyric: String) {
        if (chord.isNotBlank()) result.add(SheetLine(chord.trimEnd(), true))
        result.add(SheetLine(lyric.trimEnd(), false))
    }

    /**
     * Column where the pair should be cut: the last word gap of the lyric that
     * fits, moved back when a chord name would be split in half.
     */
    private fun cutFor(chord: String, lyric: String, columns: Int): Int {
        val ranges = chordRanges(chord)
        for (index in minOf(columns, lyric.length - 1) downTo 2) {
            if (!lyric[index - 1].isWhitespace()) continue
            if (ranges.any { it.first < index && it.last + 1 > index }) continue
            return index
        }

        val limit = minOf(columns, lyric.length)
        val straddling = ranges.firstOrNull { it.first < limit && it.last + 1 > limit }
        return when {
            straddling == null -> limit
            straddling.last + 1 <= columns -> straddling.last + 1
            else -> straddling.first.coerceAtLeast(1)
        }
    }

    /** Wraps a lyric line that has no chords above it. */
    private fun wrapText(text: String, columns: Int): List<String> {
        if (text.length <= columns) return listOf(text)
        val pieces = mutableListOf<String>()
        var rest = text
        while (rest.length > columns) {
            var cut = columns
            while (cut > 1 && !rest[cut - 1].isWhitespace()) cut--
            if (cut <= 1) cut = columns
            pieces.add(rest.take(cut))
            rest = rest.drop(cut)
        }
        if (rest.isNotEmpty()) pieces.add(rest)
        return pieces
    }

    /** Wraps a chord line on its own, never splitting a chord name. */
    private fun wrapChordLine(text: String, columns: Int): List<String> {
        if (text.length <= columns) return listOf(text)
        val pieces = mutableListOf<String>()
        var rest = text
        while (rest.length > columns) {
            val ranges = chordRanges(rest)
            var cut = 0
            for (index in minOf(columns, rest.length - 1) downTo 2) {
                if (!rest[index - 1].isWhitespace()) continue
                if (ranges.any { it.first < index && it.last + 1 > index }) continue
                cut = index
                break
            }
            if (cut == 0) cut = columns
            pieces.add(rest.take(cut))
            rest = rest.drop(cut).trimStart()
        }
        if (rest.isNotEmpty()) pieces.add(rest)
        return pieces
    }

    /** Start and end of every non-blank token in a chord line. */
    private fun chordRanges(line: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < line.length) {
            if (line[index].isWhitespace()) {
                index++
                continue
            }
            var end = index
            while (end < line.length && !line[end].isWhitespace()) end++
            ranges.add(index until end)
            index = end
        }
        return ranges
    }

    private fun String.indentWidth(): Int = length - trimStart().length

    /** ASCII tablature line: runs of dashes, or a fret line such as "e|---3---|". */
    fun isTabLine(line: String): Boolean = looksLikeTab(line.trim())

    /** Returns the capo fret declared in the sheet, if any (e.g. "Capo 2"). */
    fun capoOf(text: String): Int? {
        val match = Regex("(?i)(?:capo|capotraste)\\D{0,12}(\\d+)").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.length > 120) return false
        if (looksLikeTab(trimmed)) return false

        val (label, body) = splitLabel(trimmed)
        if (label != null && NOT_CHORD_LABELS.any { label.contains(it, ignoreCase = true) }) return false

        val tokens = body.split(' ', '\t', '|').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        var chords = 0
        for (token in tokens) {
            when {
                Chords.isChord(token) -> chords++
                token in SEPARATORS -> Unit
                else -> return false
            }
        }
        return chords > 0
    }

    fun transposeLine(line: String, semitones: Int): String {
        if (semitones % 12 == 0) return line
        if (looksLikeTab(line)) return line

        val (label, body) = splitLabel(line)
        val builder = StringBuilder(label.orEmpty())
        val offset = builder.length

        var index = 0
        while (index < body.length) {
            if (body[index].isWhitespace()) {
                index++
                continue
            }
            var end = index
            while (end < body.length && !body[end].isWhitespace()) end++

            val token = body.substring(index, end)
            val current = builder.length - offset
            when {
                current < index -> builder.append(" ".repeat(index - current))
                current > 0 -> builder.append(' ')
            }
            builder.append(transposeToken(token, semitones))
            index = end
        }
        return builder.toString()
    }

    private fun transposeToken(token: String, semitones: Int): String =
        if (token.contains('|')) {
            token.split('|').joinToString("|") { Chords.transpose(it, semitones) }
        } else {
            Chords.transpose(token, semitones)
        }

    /**
     * ASCII tablature has runs of dashes or tildes and must never be touched:
     * transposing "e|---3---|" would corrupt the fingering.
     */
    private fun looksLikeTab(line: String): Boolean {
        var dashes = 0
        var tildes = 0
        for (character in line) {
            when (character) {
                '-' -> dashes++
                '~' -> tildes++
            }
        }
        if (dashes >= 4 || tildes >= 3) return true
        return line.contains('|') && dashes >= 2
    }

    /** Splits a leading label such as "Intro:" or "[Chorus]:" from the chords. */
    private fun splitLabel(line: String): Pair<String?, String> {
        val colon = line.indexOf(':')
        if (colon <= 0 || colon > 30) return null to line
        val label = line.substring(0, colon + 1)
        if (label.any { it.isDigit() }) return null to line
        return label to line.substring(colon + 1)
    }
}
