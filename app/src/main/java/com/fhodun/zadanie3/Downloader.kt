package com.fhodun.zadanie3

import com.fhodun.zadanie3.DownloadProgress

interface Downloader {
    suspend fun download(url: String, onProgress: (DownloadProgress) -> Unit)
}