package io.github.figs0203.afinador.songbook

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import io.github.figs0203.afinador.R
import io.github.figs0203.afinador.ui.applyEdgeToEdge

/**
 * Shows a saved chord sheet and lets the reader move the whole song up or down
 * by semitones. The chosen transposition is remembered per song.
 */
class SongViewActivity : Activity() {

    private lateinit var sheet: TextView
    private lateinit var transposeLabel: TextView
    private lateinit var transposeDown: TextView
    private lateinit var transposeUp: TextView
    private lateinit var titleView: TextView
    private lateinit var artistView: TextView
    private lateinit var sourceView: TextView

    private val store by lazy { SongStore(this) }
    private val playlistStore by lazy { PlaylistStore(this) }
    private var song: Song? = null
    private var semitones = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_view)
        applyEdgeToEdge(findViewById(R.id.root), bottom = 8)

        sheet = findViewById(R.id.sheet)
        transposeLabel = findViewById(R.id.transposeLabel)
        transposeDown = findViewById(R.id.transposeDown)
        transposeUp = findViewById(R.id.transposeUp)
        titleView = findViewById(R.id.songTitle)
        artistView = findViewById(R.id.songArtist)
        sourceView = findViewById(R.id.songSource)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.addToPlaylistButton).setOnClickListener { song?.let { addToPlaylist(it) } }

        val current = intent.getStringExtra(EXTRA_SONG_ID)?.let { store.get(it) }
        if (current == null) {
            finish()
            return
        }

        song = current
        semitones = current.transposeSemitones
        titleView.text = current.title
        artistView.text = current.artist.ifBlank { getString(R.string.song_empty_artist) }
        val capo = ChordSheet.capoOf(current.text)
        sourceView.text = if (capo == null) {
            getString(R.string.song_source, current.source)
        } else {
            getString(R.string.song_source, current.source) + "  ·  " +
                getString(R.string.song_capo, capo)
        }

        transposeDown.setOnClickListener { shift(-1) }
        transposeUp.setOnClickListener { shift(1) }
        transposeLabel.setOnClickListener { setTranspose(0) }

        updateTransposeLabel()
        render()
    }

    private fun shift(delta: Int) = setTranspose((semitones + delta).coerceIn(MIN_SEMITONES, MAX_SEMITONES))

    private fun setTranspose(value: Int) {
        if (value == semitones) return
        semitones = value
        song?.let { store.updateTranspose(it.id, value) }
        updateTransposeLabel()
        render()
    }

    private fun updateTransposeLabel() {
        transposeLabel.text = when {
            semitones == 0 -> getString(R.string.song_transpose_original)
            semitones > 0 -> getString(R.string.song_transpose_up_value, semitones)
            else -> getString(R.string.song_transpose_down_value, -semitones)
        }
        transposeLabel.setTextColor(getColor(if (semitones == 0) R.color.text_secondary else R.color.accent))
        transposeDown.alpha = if (semitones <= MIN_SEMITONES) 0.35f else 1f
        transposeUp.alpha = if (semitones >= MAX_SEMITONES) 0.35f else 1f
    }

    private fun render() {
        val current = song ?: return
        val builder = SpannableStringBuilder()

        ChordSheet.render(current.text, semitones).forEach { line ->
            val start = builder.length
            builder.append(line.text)
            val end = builder.length
            if (line.isChordLine && end > start) {
                builder.setSpan(
                    ForegroundColorSpan(getColor(R.color.accent)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.append('\n')
        }
        sheet.text = builder
    }

    private fun addToPlaylist(song: Song) {
        val playlists = playlistStore.all()
        val names = playlists.map { it.name }.toMutableList()
        names.add(getString(R.string.playlists_create_and_add))

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.playlists_add_to_title, song.title))
            .setItems(names.toTypedArray()) { _, which ->
                if (which == playlists.size) {
                    promptCreateAndAdd(song)
                } else {
                    playlistStore.addSong(playlists[which].id, song.id)
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.playlists_added, playlists[which].name),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun promptCreateAndAdd(song: Song) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.playlists_name_hint)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.playlists_new)
            .setView(input)
            .setNegativeButton(R.string.songbook_cancel, null)
            .setPositiveButton(R.string.playlists_create_action) { _, _ ->
                val playlist = playlistStore.create(input.text.toString())
                playlistStore.addSong(playlist.id, song.id)
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.playlists_added, playlist.name),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SONG_ID = "io.github.figs0203.afinador.song_id"
        private const val MIN_SEMITONES = -11
        private const val MAX_SEMITONES = 11
    }
}
