package com.fhodun.zadanie3

data class DownloadProgress(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val filePath: String? = null,
    val errorMessage: String? = null
) {
    val hasTotal: Boolean get() = totalBytes > 0

    fun percent(): Int =
        if (totalBytes > 0) ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100)
            .toInt()
            .coerceIn(0, 100) else 0

    fun fraction(): Float =
        if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat() else 0f

    val isDone: Boolean get() = status == DownloadStatus.DONE
    val isError: Boolean get() = status == DownloadStatus.ERROR
}