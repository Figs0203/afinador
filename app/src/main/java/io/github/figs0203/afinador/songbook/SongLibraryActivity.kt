package io.github.figs0203.afinador.songbook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import io.github.figs0203.afinador.R
import io.github.figs0203.afinador.ui.applyEdgeToEdge

/**
 * The songbook: every imported chord sheet lives on the phone, so it opens
 * instantly and without ads. Deletes ask for confirmation.
 */
class SongLibraryActivity : Activity() {

    private lateinit var songList: android.widget.LinearLayout
    private lateinit var subtitle: TextView
    private val store by lazy { SongStore(this) }
    private val playlistStore by lazy { PlaylistStore(this) }
    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_library)
        applyEdgeToEdge(findViewById(R.id.root))

        songList = findViewById(R.id.songList)
        subtitle = findViewById(R.id.librarySubtitle)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.importButton).setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
        findViewById<View>(R.id.playlistsButton).setOnClickListener {
            startActivity(Intent(this, PlaylistsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val songs = store.all()
        songList.removeAllViews()
        subtitle.text = if (songs.isEmpty()) {
            getString(R.string.songbook_subtitle)
        } else {
            getString(R.string.songbook_count, songs.size)
        }

        if (songs.isEmpty()) {
            songList.addView(emptyHint())
            return
        }

        songs.forEachIndexed { index, song ->
            if (index > 0) songList.addView(divider())
            songList.addView(songRow(song))
        }
    }

    private fun emptyHint(): View = TextView(this).apply {
        text = getString(R.string.songbook_empty)
        setTextColor(getColor(R.color.text_secondary))
        textSize = 13f
        gravity = android.view.Gravity.CENTER
        setLineSpacing(dp(5).toFloat(), 1f)
        setPadding(0, dp(48), 0, dp(24))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.stroke))
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )
    }

    private fun songRow(song: Song): View {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { open(song) }
        }

        val texts = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        texts.addView(TextView(this).apply {
            text = song.title
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val artist = song.artist.ifBlank { getString(R.string.song_empty_artist) }
        texts.addView(TextView(this).apply {
            text = "$artist · ${song.source}"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        })

        row.addView(
            texts,
            android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row.addView(TextView(this).apply {
            text = "+"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            contentDescription = getString(R.string.song_add_to_playlist)
            setPadding(dp(12), dp(8), dp(4), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { addToPlaylist(song) }
        })

        row.addView(TextView(this).apply {
            text = "✕"
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            contentDescription = getString(R.string.songbook_delete)
            setPadding(dp(12), dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmDelete(song) }
        })

        return row
    }

    private fun open(song: Song) {
        startActivity(
            Intent(this, SongViewActivity::class.java)
                .putExtra(SongViewActivity.EXTRA_SONG_ID, song.id)
        )
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

    private fun confirmDelete(song: Song) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.songbook_delete_title)
            .setMessage(getString(R.string.songbook_delete_message, song.title))
            .setNegativeButton(R.string.songbook_cancel, null)
            .setPositiveButton(R.string.songbook_delete_confirm) { _, _ ->
                store.delete(song.id)
                refresh()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
