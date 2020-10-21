package com.github.adamantcheese.chan.core.cache

import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.adamantcheese.chan.core.cache.downloader.FileDownloadRequest
import com.github.k1rakishou.fsaf.file.RawFile
import com.nhaarman.mockitokotlin2.spy
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val executor = Executors.newSingleThreadExecutor()

internal fun withServer(func: (MockWebServer) -> Unit) {
    val server = MockWebServer()

    try {
        func(server)
    } finally {
        server.shutdown()
    }
}

internal fun createFileDownloadRequest(
    url: HttpUrl,
    chunksCount: Int = 1,
    file: RawFile
): FileDownloadRequest {
    return spy(
        FileDownloadRequest(
            url,
            file,
            AtomicInteger(chunksCount),
            AtomicLong(0),
            AtomicLong(0),
            CancelableDownload(url),
            DownloadRequestExtraInfo()
        )
    )
}