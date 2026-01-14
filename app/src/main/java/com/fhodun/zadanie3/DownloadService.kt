package com.fhodun.zadanie3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.fhodun.zadanie3.data.downloader.HttpUrlConnectionDownloader
import com.fhodun.zadanie3.data.repository.DownloadProgressRepository
import com.fhodun.zadanie3.domain.model.DownloadProgress
import com.fhodun.zadanie3.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class DownloadService : Service() {
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIF_ID = 1
        private const val TAG = "DownloadService"
        const val EXTRA_URL = "url"
        private const val NOTIF_MIN_INTERVAL_MS = 300L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val urlString = intent?.getStringExtra(EXTRA_URL)
        Log.d(TAG, "onStartCommand, url=$urlString")

        if (urlString.isNullOrBlank()) {
            Log.e(TAG, "Brak URL w intent")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIF_ID,
            buildNotification(progress = 0, total = 0, title = "Rozpoczynam pobieranie", contentIntent = null)
        )

        val downloader = HttpUrlConnectionDownloader(appContext = applicationContext)

        serviceScope.launch {
            var lastNotifAt = 0L
            var lastNotifPct = -1

            try {
                downloader.download(urlString) { progress ->
                    // UI (Compose) obserwuje to repo
                    DownloadProgressRepository.emitProgress(progress)

                    val now = SystemClock.elapsedRealtime()
                    val pct = percent(progress)
                    val shouldNotify = pct != lastNotifPct || (now - lastNotifAt) >= NOTIF_MIN_INTERVAL_MS

                    if (shouldNotify) {
                        lastNotifPct = pct
                        lastNotifAt = now
                        updateNotificationFromProgress(progress)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Nieobsłużony błąd pobierania", t)
                val error = DownloadProgress(
                    downloadedBytes = 0,
                    totalBytes = 0,
                    status = DownloadStatus.ERROR,
                    errorMessage = t.message ?: t::class.java.simpleName
                )
                DownloadProgressRepository.emitProgress(error)
                updateNotificationFromProgress(error)
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun percent(progress: DownloadProgress): Int {
        val total = progress.totalBytes
        return if (total > 0) {
            ((progress.downloadedBytes.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else 0
    }

    private fun updateNotificationFromProgress(progress: DownloadProgress) {
        val title = when (progress.status) {
            DownloadStatus.DONE -> "Pobieranie zakończone"
            DownloadStatus.ERROR -> "Błąd pobierania"
            DownloadStatus.RUNNING -> "Pobieranie pliku"
            DownloadStatus.IDLE -> "Pobieranie"
        }

        val contentIntent = if (progress.status == DownloadStatus.DONE && !progress.filePath.isNullOrBlank()) {
            val file = File(progress.filePath)
            val openIntent = buildOpenFileIntent(file)
            PendingIntent.getActivity(
                this,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        updateNotification(
            progress = progress.downloadedBytes,
            total = progress.totalBytes,
            title = title,
            contentIntent = contentIntent
        )
    }

    private fun buildOpenFileIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val guessedMime = guessMimeFromExtension(file.name)

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun guessMimeFromExtension(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun updateNotification(progress: Long, total: Long, title: String = "Pobieranie pliku", contentIntent: PendingIntent?) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notif = buildNotification(progress, total, title, contentIntent)
        manager.notify(NOTIF_ID, notif)
    }

    private fun buildNotification(progress: Long, total: Long, title: String, contentIntent: PendingIntent?): Notification {
        val pct = if (total > 0) ((progress.toDouble() / total.toDouble()) * 100).toInt() else 0

        val fallbackIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fallbackPending = PendingIntent.getActivity(
            this,
            0,
            fallbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pi = contentIntent ?: fallbackPending

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (total > 0) "Pobrano: $progress / $total" else "Pobrano: $progress")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)

        val finished = title.contains("zakończone", ignoreCase = true)
        val error = title.contains("błąd", ignoreCase = true)

        if (finished || error) {
            builder.setOngoing(false)
                .setAutoCancel(true)
                .setProgress(0, 0, false)
        } else {
            builder.setOngoing(true)
            if (total > 0) {
                builder.setProgress(100, pct.coerceIn(0, 100), false)
            } else {
                builder.setProgress(0, 0, true)
            }
        }

        if (finished && contentIntent != null) {
            builder.addAction(android.R.drawable.ic_menu_view, "Otwórz", contentIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pobieranie",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
