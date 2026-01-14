package com.fhodun.zadanie3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object FileInfoFetcher {
    suspend fun fetch(urlString: String): Result<FileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            conn.connect()

            val length = conn.contentLengthLong
            val type = conn.contentType

            conn.disconnect()

            FileInfo(
                contentType = type,
                contentLengthBytes = if (length >= 0) length else null
            )
        }
    }
}