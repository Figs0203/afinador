package io.github.figs0203.afinador.songbook

import io.github.figs0203.afinador.music.Chords

/** One rendered line of a chord sheet, flagged when it only holds chords. */
data class SheetLine(val text: String, val isChordLine: Boolean)

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
