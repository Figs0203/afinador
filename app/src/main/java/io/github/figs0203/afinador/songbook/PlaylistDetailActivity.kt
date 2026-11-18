package io.github.figs0203.afinador.songbook

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.github.figs0203.afinador.R
import io.github.figs0203.afinador.ui.applyEdgeToEdge

class PlaylistDetailActivity : Activity() {

    private lateinit var songList: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private val playlistStore by lazy { PlaylistStore(this) }
    private val songStore by lazy { SongStore(this) }
    private val density by lazy { resources.displayMetrics.density }
    private var playlistId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)
        applyEdgeToEdge(findViewById(R.id.root))

        songList = findViewById(R.id.songList)
        titleView = findViewById(R.id.playlistTitle)
        subtitleView = findViewById(R.id.playlistSubtitle)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.renameButton).setOnClickListener { promptRename() }
        findViewById<View>(R.id.deleteButton).setOnClickListener { confirmDelete() }

        playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val id = playlistId ?: return
        val playlist = playlistStore.get(id)
        if (playlist == null) { finish(); return }
        titleView.text = playlist.name
        val songs = playlist.songIds.mapNotNull { songStore.get(it) }
        subtitleView.text = getString(R.string.playlists_song_count, songs.size)
        songList.removeAllViews()

        if (songs.isEmpty()) {
            songList.addView(emptyHint())
            return
        }

        songs.forEachIndexed { index, song ->
            if (index > 0) songList.addView(divider())
            songList.addView(songRow(playlist, song))
        }
    }

    private fun emptyHint(): View = TextView(this).apply {
        text = getString(R.string.playlists_detail_empty)
        setTextColor(getColor(R.color.text_secondary))
        textSize = 13f
        gravity = android.view.Gravity.CENTER
        setLineSpacing(dp(5).toFloat(), 1f)
        setPadding(0, dp(48), 0, dp(24))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(getColor(R.color.stroke))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun songRow(playlist: Playlist, song: Song): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { open(song) }
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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

        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = "✕"
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            contentDescription = getString(R.string.songbook_delete)
            setPadding(dp(16), dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { playlistStore.removeSong(playlist.id, song.id); refresh() }
        })

        return row
    }

    private fun open(song: Song) {
        startActivity(
            Intent(this, SongViewActivity::class.java)
                .putExtra(SongViewActivity.EXTRA_SONG_ID, song.id)
        )
    }

    private fun promptRename() {
        val playlist = playlistStore.get(playlistId ?: return) ?: return
        val input = EditText(this).apply {
            setText(playlist.name)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.playlists_rename)
            .setView(input)
            .setNegativeButton(R.string.songbook_cancel, null)
            .setPositiveButton(R.string.playlists_create_action) { _, _ ->
                playlistStore.rename(playlist.id, input.text.toString())
                refresh()
            }
            .show()
    }

    private fun confirmDelete() {
        val playlist = playlistStore.get(playlistId ?: return) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.playlists_delete_title)
            .setMessage(getString(R.string.playlists_delete_message, playlist.name))
            .setNegativeButton(R.string.songbook_cancel, null)
            .setPositiveButton(R.string.songbook_delete_confirm) { _, _ ->
                playlistStore.delete(playlist.id)
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        const val EXTRA_PLAYLIST_ID = "io.github.figs0203.afinador.playlist_id"
    }
}