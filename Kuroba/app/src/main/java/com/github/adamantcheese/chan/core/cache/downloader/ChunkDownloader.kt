package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.io.IOException

internal class ChunkDownloader(
        private val okHttpClient: OkHttpClient,
        private val activeDownloads: ActiveDownloads
) {

    fun downloadChunk(
            url: HttpUrl,
            chunk: Chunk,
            totalChunksCount: Int
    ): Flowable<Response> {
        val request = activeDownloads.get(url)
                ?: activeDownloads.throwCancellationException(url)

        if (chunk.isWholeFile() && totalChunksCount > 1) {
            throw IllegalStateException("downloadChunk() Bad amount of chunks, " +
                    "should be only one but actual = $totalChunksCount")
        }

        if (ChanSettings.verboseLogs.get()) {
            log(TAG, "Start downloading (${maskImageUrl(url)}), chunk ${chunk.start}..${chunk.end}")
        }

        val builder = Request.Builder()
                .url(url)
                .header("User-Agent", NetModule.USER_AGENT)

        if (!chunk.isWholeFile()) {
            // If chunk.isWholeFile == true that means that either the file size is too small (
            // and there is no reason to download it in chunks) (it should be less than
            // [FileCacheV2.MIN_CHUNK_SIZE]) or that the server does not support Partial Content
            // or the user turned off chunked file downloading, or we couldn't send HEAD request
            // (it was timed out) so we should download it normally.
            builder.header("Range", "bytes=" + chunk.start + "-" + chunk.end)
        }

        val httpRequest = builder.build()
        val startTime = System.currentTimeMillis()

        return Flowable.create({ emitter ->
            BackgroundUtils.ensureBackgroundThread()

            val serializedEmitter = emitter.serialize()
            val call = okHttpClient.newBuilder()
                    .addInterceptor(Interceptor.invoke { chain ->
                        val response = chain.proceed(chain.request())
                        if ("MISS" == response.header(CF_CACHE_STATUS_HEADER)) {
                            log(TAG, "CF cache miss, retrying immediately")
                            response.closeQuietly()
                            return@invoke chain.proceed(chain.request()) // TODO #1071 this might not be the right solution
                        }
                        return@invoke response
                    }).build()
                    .newCall(httpRequest)

            // This function will be used to cancel a CHUNK (not the whole file) download upon
            // cancellation
            val disposeFunc = {
                BackgroundUtils.ensureBackgroundThread()

                if (ChanSettings.verboseLogs.get() && !call.isCanceled()) {
                    log(
                            TAG,
                            "Disposing OkHttp Call for CHUNKED request $request via " +
                                    "manual canceling (${chunk.start}..${chunk.end})"
                    )

                    call.cancel()
                }
            }

            val downloadState = activeDownloads.addDisposeFunc(url, disposeFunc)
            if (downloadState != DownloadState.Running) {
                when (downloadState) {
                    DownloadState.Canceled -> activeDownloads.get(url)?.cancelableDownload?.cancel()
                    DownloadState.Stopped -> activeDownloads.get(url)?.cancelableDownload?.stop()
                    else -> {
                        serializedEmitter.tryOnError(
                                RuntimeException("DownloadState must be either Stopped or Canceled")
                        )
                        return@create
                    }
                }

                serializedEmitter.tryOnError(
                        FileCacheException.CancellationException(
                                activeDownloads.getState(url),
                                url)
                )
                return@create
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val diff = System.currentTimeMillis() - startTime
                    val exceptionMessage = e.message ?: "No message"

                    log(TAG,
                            "Couldn't get chunk response, reason = ${e.javaClass.simpleName} ($exceptionMessage)" +
                                    " (${maskImageUrl(url)}) ${chunk.start}..${chunk.end}, time = ${diff}ms"
                    )

                    if (!isCancellationError(e)) {
                        serializedEmitter.tryOnError(e)
                    } else {
                        serializedEmitter.tryOnError(
                                FileCacheException.CancellationException(
                                        activeDownloads.getState(url),
                                        url
                                )
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (ChanSettings.verboseLogs.get()) {
                        val diff = System.currentTimeMillis() - startTime
                        log(TAG, "Got chunk response in (${maskImageUrl(url)}) " +
                                "${chunk.start}..${chunk.end} in ${diff}ms")
                    }

                    serializedEmitter.onNext(response)
                    serializedEmitter.onComplete()
                }
            })
        }, BackpressureStrategy.BUFFER)
    }

    companion object {
        private const val TAG = "ChunkDownloader"
        private const val CF_CACHE_STATUS_HEADER = "CF-Cache-Status"
    }
}