package com.fhodun.zadanie3

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

class HttpUrlConnectionDownloader(
    private val appContext: Context,
    private val uiMinIntervalMs: Long = 100L
) : Downloader {

    override suspend fun download(url: String, onProgress: (DownloadProgress) -> Unit) {
        withContext(Dispatchers.IO) {
            downloadInternal(url, onProgress)
        }
    }

    private fun downloadInternal(urlString: String, onProgress: (DownloadProgress) -> Unit) {
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

            val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: appContext.filesDir
            if (!dir.exists()) dir.mkdirs()

            val filename = buildFileName(
                urlString = urlString,
                contentDisposition = connection.getHeaderField("Content-Disposition"),
                mime = mime
            )
            val outFile = File(dir, filename)

            var lastUiAt = 0L
            fun emit(progress: DownloadProgress, force: Boolean = false) {
                val now = SystemClock.elapsedRealtime()
                if (force || (now - lastUiAt) >= uiMinIntervalMs) {
                    lastUiAt = now
                    onProgress(progress)
                }
            }

            emit(
                DownloadProgress(
                    downloadedBytes = 0,
                    totalBytes = if (fileLength > 0) fileLength else 0,
                    status = DownloadStatus.RUNNING,
                    filePath = outFile.absolutePath,
                    errorMessage = null
                ),
                force = true
            )

            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total: Long = 0

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        total += read

                        emit(
                            DownloadProgress(
                                downloadedBytes = total,
                                totalBytes = if (fileLength > 0) fileLength else 0,
                                status = DownloadStatus.RUNNING,
                                filePath = outFile.absolutePath
                            )
                        )
                    }

                    output.flush()
                }
            }

            val finalSize = if (fileLength > 0) fileLength else outFile.length()
            if (!outFile.exists() || outFile.length() <= 0L) {
                emit(
                    DownloadProgress(
                        downloadedBytes = 0,
                        totalBytes = 0,
                        status = DownloadStatus.ERROR,
                        filePath = outFile.absolutePath,
                        errorMessage = "Plik nie został zapisany. Ścieżka: ${outFile.absolutePath}"
                    ),
                    force = true
                )
                return
            }

            emit(
                DownloadProgress(
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    status = DownloadStatus.DONE,
                    filePath = outFile.absolutePath
                ),
                force = true
            )

        } finally {
            connection?.disconnect()
        }
    }

    private fun buildFileName(
        urlString: String,
        contentDisposition: String?,
        mime: String?
    ): String {
        val fromCd = extractFilenameFromContentDisposition(contentDisposition)
        val rawFromUrl = urlString.substringAfterLast('/').substringBefore('?').trim()

        val base = (fromCd?.takeIf { it.isNotBlank() } ?: rawFromUrl).ifBlank {
            "download_${System.currentTimeMillis()}"
        }

        val safeBase = base.replace(Regex("[^A-Za-z0-9._-]"), "_")

        if (safeBase.contains('.') && safeBase.substringAfterLast('.').length in 1..6) {
            return safeBase
        }

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

    private fun extractFilenameFromContentDisposition(contentDisposition: String?): String? {
        val cd = contentDisposition.orEmpty()

        val match = Regex(
            """filename\*=UTF-8''([^;]+)|filename="?([^;\"]+)"?""",
            RegexOption.IGNORE_CASE
        ).find(cd)

        val raw = match?.groups?.get(1)?.value ?: match?.groups?.get(2)?.value
        val trimmed = raw?.trim()?.trim('"')

        return trimmed?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }
    }
}