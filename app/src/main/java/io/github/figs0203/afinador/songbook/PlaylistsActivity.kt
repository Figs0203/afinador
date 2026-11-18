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

class PlaylistsActivity : Activity() {

    private lateinit var playlistList: LinearLayout
    private lateinit var subtitle: TextView
    private val store by lazy { PlaylistStore(this) }
    private val songStore by lazy { SongStore(this) }
    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        applyEdgeToEdge(findViewById(R.id.root))

        playlistList = findViewById(R.id.playlistList)
        subtitle = findViewById(R.id.playlistsSubtitle)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.newPlaylistButton).setOnClickListener { promptCreate() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val playlists = store.all()
        playlistList.removeAllViews()
        subtitle.text = if (playlists.isEmpty()) {
            getString(R.string.playlists_subtitle)
        } else {
            getString(R.string.playlists_count, playlists.size)
        }

        if (playlists.isEmpty()) {
            playlistList.addView(emptyHint())
            return
        }

        playlists.forEachIndexed { index, playlist ->
            if (index > 0) playlistList.addView(divider())
            playlistList.addView(playlistRow(playlist))
        }
    }

    private fun emptyHint(): View = TextView(this).apply {
        text = getString(R.string.playlists_empty)
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

    private fun playlistRow(playlist: Playlist): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { open(playlist) }
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = playlist.name
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val count = playlist.songIds.count { songStore.get(it) != null }
        texts.addView(TextView(this).apply {
            text = getString(R.string.playlists_song_count, count)
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            maxLines = 1
            setPadding(0, dp(3), 0, 0)
        })

        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = "✕"
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.text_secondary))
            contentDescription = getString(R.string.playlists_delete)
            setPadding(dp(16), dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmDelete(playlist) }
        })

        return row
    }

    private fun open(playlist: Playlist) {
        startActivity(
            Intent(this, PlaylistDetailActivity::class.java)
                .putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.id)
        )
    }

    private fun promptCreate() {
        val input = EditText(this).apply {
            hint = getString(R.string.playlists_name_hint)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.playlists_new)
            .setView(input)
            .setNegativeButton(R.string.songbook_cancel, null)
            .setPositiveButton(R.string.playlists_create_action) { _, _ ->
                val name = input.text.toString()
                store.create(name)
                refresh()
            }
            .show()
    }

    private fun confirmDelete(playlist: Playlist) {
        AlertDialog.Builder(this)
            .setTitle(R.string.playlists_delete_title)
            .setMessage(getString(R.string.playlists_delete_message, playlist.name))
            .setNegativeButton(R.string.songbook_cancel, null)
            .setPositiveButton(R.string.songbook_delete_confirm) { _, _ ->
                store.delete(playlist.id)
                refresh()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}