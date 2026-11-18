package io.github.figs0203.afinador.songbook

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import io.github.figs0203.afinador.R
import io.github.figs0203.afinador.songbook.SongLibraryActivity

class PlaylistImportService : Service() {

    companion object {
        const val ACTION_START = "io.github.figs0203.afinador.action.START_PLAYLIST_IMPORT"
        const val EXTRA_URL = "playlist_url"

        const val BROADCAST_PROGRESS = "io.github.figs0203.afinador.PLAYLIST_IMPORT_PROGRESS"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_CURRENT = "current"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DONE = "done"
        const val EXTRA_ADDED = "added"
        const val EXTRA_SKIPPED = "skipped"
        const val EXTRA_FAILED = "failed"
        const val EXTRA_ERROR = "error"

        private const val CHANNEL_ID = "playlist_import"
        private const val NOTIF_ID = 4242

        fun start(context: Context, url: String) {
            val intent = Intent(context, PlaylistImportService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playlist_import_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification("Preparando importación…", 0, 0, true))
        Thread { runImport(url, startId) }.start()
        return START_NOT_STICKY
    }

    private fun runImport(url: String, startId: Int) {
        var added = 0
        var skipped = 0
        var failed = 0
        try {
            val playlist = PlaylistImporter.importFromUrl(url)
            val total = playlist.entries.size
            if (total == 0) {
                broadcast(done = true, added = 0, skipped = 0, failed = 0, error = getString(R.string.playlist_empty))
                notifyFinal(getString(R.string.playlist_empty))
                return
            }

            val store = SongStore(applicationContext)
            val knownLinks = store.all().mapNotNull { it.url }.toMutableSet()
            val songs = mutableListOf<Song>()

            playlist.entries.forEachIndexed { index, entry ->
                val current = index + 1
                val msg = getString(R.string.playlist_progress, current, total, entry.title)
                updateNotification(msg, current, total, false)
                broadcast(
                    done = false,
                    total = total,
                    current = current,
                    title = entry.title,
                    added = songs.size,
                    skipped = skipped,
                    failed = failed
                )

                if (!knownLinks.add(entry.url)) {
                    skipped++
                    return@forEachIndexed
                }

                val imported = runCatching { SongImporter.importFromUrl(entry.url) }.getOrNull()
                if (imported == null) {
                    failed++
                    return@forEachIndexed
                }

                songs.add(
                    Song(
                        id = java.util.UUID.randomUUID().toString(),
                        title = imported.title.ifBlank { entry.title }
                            .ifBlank { getString(R.string.song_unknown_title) }.take(120),
                        artist = imported.artist.ifBlank { entry.artist }.take(120),
                        source = SongImporter.labelFor(entry.url),
                        url = entry.url,
                        text = imported.text,
                        savedAt = System.currentTimeMillis()
                    )
                )
            }

            store.addAll(songs)
            added = songs.size

            val summary = buildSummary(added, skipped, failed)
            broadcast(done = true, total = total, current = total, title = "", added = added, skipped = skipped, failed = failed, error = null)
            notifyFinal(summary)
        } catch (e: Exception) {
            val err = e.message ?: getString(R.string.playlist_failed)
            broadcast(done = true, total = 0, current = 0, title = "", added = 0, skipped = 0, failed = 0, error = err)
            notifyFinal(err)
        } finally {
            stopSelf(startId)
        }
    }

    private fun broadcast(
        done: Boolean,
        total: Int = 0,
        current: Int = 0,
        title: String = "",
        added: Int = 0,
        skipped: Int = 0,
        failed: Int = 0,
        error: String? = null
    ) {
        val intent = Intent(BROADCAST_PROGRESS).apply {
            putExtra(EXTRA_DONE, done)
            putExtra(EXTRA_TOTAL, total)
            putExtra(EXTRA_CURRENT, current)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ADDED, added)
            putExtra(EXTRA_SKIPPED, skipped)
            putExtra(EXTRA_FAILED, failed)
            if (error != null) putExtra(EXTRA_ERROR, error)
        }
        sendBroadcast(intent)
    }

    private fun notificationBuilder(text: String, icon: Int): Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.playlist_import_notification_title))
            .setContentText(text)
    }

    private fun buildNotification(text: String, current: Int, total: Int, indeterminate: Boolean = false): Notification {
        val builder = notificationBuilder(text, android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (total > 0) {
            builder.setProgress(total, current.coerceIn(0, total), indeterminate && current == 0)
        } else if (indeterminate) {
            builder.setProgress(0, 0, true)
        }
        val openIntent = Intent(this, SongLibraryActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pending)
        return builder.build()
    }

    private fun updateNotification(text: String, current: Int, total: Int, indeterminate: Boolean = false) {
        notificationManager?.notify(NOTIF_ID, buildNotification(text, current, total, indeterminate))
    }

    private fun notifyFinal(text: String) {
        val builder = notificationBuilder(text, android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
        val openIntent = Intent(this, SongLibraryActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pending)
        notificationManager?.notify(NOTIF_ID, builder.build())
        // Remove foreground state but keep the final notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    private fun buildSummary(added: Int, skipped: Int, failed: Int): String = buildString {
        append(getString(R.string.playlist_summary_added, added))
        if (skipped > 0) append(" · ").append(
            getString(
                if (skipped == 1) R.string.playlist_summary_skipped_one else R.string.playlist_summary_skipped,
                skipped
            )
        )
        if (failed > 0) append(" · ").append(
            getString(
                if (failed == 1) R.string.playlist_summary_failed_one else R.string.playlist_summary_failed,
                failed
            )
        )
    }
}