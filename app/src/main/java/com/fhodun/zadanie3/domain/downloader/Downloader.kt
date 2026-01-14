package com.fhodun.zadanie3.domain.downloader

import com.fhodun.zadanie3.domain.model.DownloadProgress

interface Downloader {
    suspend fun download(url: String, onProgress: (DownloadProgress) -> Unit)
}

