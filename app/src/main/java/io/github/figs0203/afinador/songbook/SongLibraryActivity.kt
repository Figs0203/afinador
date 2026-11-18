package io.github.figs0203.afinador.songbook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.figs0203.afinador.R
import io.github.figs0203.afinador.ui.applyEdgeToEdge

/**
 * The songbook: every imported chord sheet lives on the phone, so it opens
 * instantly and without ads. Deletes ask for confirmation.
 */
class SongLibraryActivity : Activity() {

    private lateinit var songList: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var subtitle: TextView
    private lateinit var searchInput: EditText
    private lateinit var sortArrival: TextView
    private lateinit var sortAlpha: TextView
    private lateinit var sortArtist: TextView
    private lateinit var alphaSidebar: LinearLayout

    private val store by lazy { SongStore(this) }
    private val playlistStore by lazy { PlaylistStore(this) }
    private val density by lazy { resources.displayMetrics.density }

    private var currentSort: String = "arrival"
    private var currentQuery: String = ""
    private var sortedSongs: List<Song> = emptyList()
    private var rowViews: List<View> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_library)
        applyEdgeToEdge(findViewById(R.id.root))

        songList = findViewById(R.id.songList)
        scrollView = findViewById(R.id.songScrollView)
        subtitle = findViewById(R.id.librarySubtitle)
        searchInput = findViewById(R.id.searchInput)
        sortArrival = findViewById(R.id.sortArrival)
        sortAlpha = findViewById(R.id.sortAlpha)
        sortArtist = findViewById(R.id.sortArtist)
        alphaSidebar = findViewById(R.id.alphaSidebar)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<View>(R.id.importButton).setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
        findViewById<View>(R.id.playlistsButton).setOnClickListener {
            startActivity(Intent(this, PlaylistsActivity::class.java))
        }

        sortArrival.setOnClickListener { setSort("arrival") }
        sortAlpha.setOnClickListener { setSort("alpha") }
        sortArtist.setOnClickListener { setSort("artist") }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                refresh()
            }
        })

        buildAlphaSidebar()
        updateSortChips()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun setSort(mode: String) {
        currentSort = mode
        updateSortChips()
        refresh()
    }

    private fun updateSortChips() {
        val selected = getColor(R.color.accent)
        val normal = getColor(R.color.text_secondary)
        sortArrival.setTextColor(if (currentSort == "arrival") selected else normal)
        sortAlpha.setTextColor(if (currentSort == "alpha") selected else normal)
        sortArtist.setTextColor(if (currentSort == "artist") selected else normal)
    }

    private fun refresh() {
        val all = store.all()
        val filtered = if (currentQuery.isBlank()) {
            all
        } else {
            all.filter {
                it.title.contains(currentQuery, ignoreCase = true) ||
                    it.artist.contains(currentQuery, ignoreCase = true)
            }
        }
        sortedSongs = when (currentSort) {
            "alpha" -> filtered.sortedBy { it.title.lowercase() }
            "artist" -> filtered.sortedBy { it.artist.lowercase().ifBlank { "zzz" } }
            else -> filtered // arrival: newest first, as stored
        }

        songList.removeAllViews()
        subtitle.text = if (all.isEmpty()) {
            getString(R.string.songbook_subtitle)
        } else {
            getString(R.string.songbook_count, all.size)
        }

        if (sortedSongs.isEmpty()) {
            songList.addView(emptyHint())
            rowViews = emptyList()
            return
        }

        val rows = mutableListOf<View>()
        sortedSongs.forEachIndexed { index, song ->
            if (index > 0) songList.addView(divider())
            val row = songRow(song)
            songList.addView(row)
            rows.add(row)
        }
        rowViews = rows
    }

    private fun buildAlphaSidebar() {
        alphaSidebar.removeAllViews()
        val letters = ('A'..'Z').map { it.toString() }
        letters.forEach { letter ->
            val tv = TextView(this).apply {
                text = letter
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 0, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { jumpToLetter(letter) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            alphaSidebar.addView(tv)
        }
    }

    private fun jumpToLetter(letter: String) {
        val index = sortedSongs.indexOfFirst {
            it.title.trim().uppercase().startsWith(letter)
        }
        if (index < 0 || index >= rowViews.size) return
        scrollView.smoothScrollTo(0, rowViews[index].top)
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
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )
    }

    private fun songRow(song: Song): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
            isClickable = true
            isFocusable = true
            setOnClickListener { open(song) }
        }

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
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
