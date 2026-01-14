package com.fhodun.zadanie3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIF_ID = 1
        private const val TAG = "DownloadService"

        const val EXTRA_URL = "url"

        // UI update throttle (ms) – żeby Compose nie był spamowany tysiącami update'ów.
        private const val UI_MIN_INTERVAL_MS = 100L

        // Notification update: nie spamuj systemu.
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

        serviceScope.launch {
            try {
                pobierzPlik(urlString)
            } catch (t: Throwable) {
                Log.e(TAG, "Nieobsłużony błąd pobierania", t)
                val info = PostepInfo(
                    mPobranychBajtow = 0,
                    mRozmiar = 0,
                    mStatus = PostepInfo.STATUS_ERROR,
                    mBlad = t.message ?: t::class.java.simpleName
                )
                sendProgress(info)
                updateNotification(0, 0, "Błąd pobierania", contentIntent = null)
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pobierzPlik(urlString: String) {
        Log.d(TAG, "Start pobierzPlik, url=$urlString")
        var connection: HttpURLConnection? = null

        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }

            val mime = connection.contentType?.substringBefore(';')?.trim()
            val fileLength = connection.contentLengthLong

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            if (!dir.exists()) dir.mkdirs()

            val filename = buildFileName(urlString, connection.getHeaderField("Content-Disposition"), mime)
            val outFile = File(dir, filename)

            val info = PostepInfo(
                mPobranychBajtow = 0,
                mRozmiar = if (fileLength > 0) fileLength else 0,
                mStatus = PostepInfo.STATUS_RUNNING,
                mPlikSciezka = outFile.absolutePath,
                mBlad = null
            )
            sendProgress(info)

            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total: Long = 0

                    Log.d(TAG, "Start download: $urlString, size=$fileLength, mime=$mime, out=${outFile.absolutePath}")

                    var lastNotifAt = 0L
                    var lastNotifPct = -1
                    var lastUiAt = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        total += read

                        val now = SystemClock.elapsedRealtime()
                        if ((now - lastUiAt) >= UI_MIN_INTERVAL_MS) {
                            lastUiAt = now
                            info.mPobranychBajtow = total
                            info.mStatus = PostepInfo.STATUS_RUNNING
                            sendProgress(info)
                        }

                        val pct = if (fileLength > 0) ((total * 100.0) / fileLength).toInt().coerceIn(0, 100) else 0
                        val shouldNotify = pct != lastNotifPct || (now - lastNotifAt) >= NOTIF_MIN_INTERVAL_MS
                        if (shouldNotify) {
                            lastNotifPct = pct
                            lastNotifAt = now
                            updateNotification(total, fileLength, title = "Pobieranie pliku", contentIntent = null)
                        }
                    }

                    output.flush()
                }
            }

            val finalSize = if (fileLength > 0) fileLength else outFile.length()
            info.mPobranychBajtow = finalSize
            info.mRozmiar = finalSize

            if (!outFile.exists() || outFile.length() <= 0L) {
                info.mStatus = PostepInfo.STATUS_ERROR
                info.mBlad = "Plik nie został zapisany. Ścieżka: ${outFile.absolutePath}"
                sendProgress(info)
                updateNotification(0, 0, "Błąd pobierania", contentIntent = null)
                Log.e(TAG, info.mBlad ?: "Plik nie został zapisany")
                return
            }

            info.mStatus = PostepInfo.STATUS_DONE
            info.mBlad = null
            sendProgress(info)

            val openIntent = buildOpenFileIntent(outFile, mime)
            val pendingOpen = PendingIntent.getActivity(
                this,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            updateNotification(
                progress = finalSize,
                total = finalSize,
                title = "Pobieranie zakończone",
                contentIntent = pendingOpen
            )

            Log.d(TAG, "Pobieranie zakończone, zapisano do: ${outFile.absolutePath} (len=${outFile.length()}, mime=$mime)")

        } catch (e: Exception) {
            Log.e(TAG, "Błąd pobierania", e)
            val info = PostepInfo(
                mPobranychBajtow = 0,
                mRozmiar = 0,
                mStatus = PostepInfo.STATUS_ERROR,
                mBlad = e.message ?: e::class.java.simpleName
            )
            sendProgress(info)
            updateNotification(0, 0, "Błąd pobierania", contentIntent = null)
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildOpenFileIntent(file: File, mime: String?): Intent {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val guessedMime = mime ?: guessMimeFromExtension(file.name)

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

    private fun buildFileName(urlString: String, contentDisposition: String?, mime: String?): String {
        // 1) Content-Disposition: filename="..." / filename*=UTF-8''...
        val cd = contentDisposition.orEmpty()
        val fromCd = Regex(
            """filename\*=UTF-8''([^;]+)|filename="?([^;"]+)"?""",
            RegexOption.IGNORE_CASE
        )
            .find(cd)
            ?.let { match ->
                match.groups[1]?.value ?: match.groups[2]?.value
            }
            ?.trim()

        val rawFromUrl = urlString.substringAfterLast('/').substringBefore('?').trim()

        val base = (fromCd?.takeIf { it.isNotBlank() } ?: rawFromUrl).ifBlank {
            "pobrany_plik_${System.currentTimeMillis()}"
        }

        val safeBase = base.replace(Regex("[^A-Za-z0-9._-]"), "_")

        // Jeśli już ma rozszerzenie, zostaw.
        if (safeBase.contains('.') && safeBase.substringAfterLast('.').length in 1..6) {
            return safeBase
        }

        // 2) Dopasuj rozszerzenie po mime
        val ext = when (mime?.lowercase(Locale.US)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            "video/mp4" -> "mp4"
            else -> "bin"
        }

        return "$safeBase.$ext"
    }

    private fun sendProgress(info: PostepInfo) {
        DownloadRepository.emitProgress(info)
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
