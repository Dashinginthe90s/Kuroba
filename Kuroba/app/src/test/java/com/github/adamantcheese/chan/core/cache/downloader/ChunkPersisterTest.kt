package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2.Companion.MIN_CHUNK_SIZE
import com.github.adamantcheese.chan.core.cache.createFileDownloadRequest
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.closeQuietly
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
class ChunkPersisterTest {
    private val testModule = TestModule()

    private lateinit var fileManager: FileManager
    private lateinit var cacheHandler: CacheHandler
    private lateinit var activeDownloads: ActiveDownloads
    private lateinit var chunkPersister: ChunkPersister
    private lateinit var chunksCacheDirFile: RawFile

    @Before
    fun init() {
        AndroidUtils.init(testModule.provideApplication())
        ShadowLog.stream = System.out

        fileManager = testModule.provideFileManager()
        cacheHandler = testModule.provideCacheHandler()
        activeDownloads = spy(testModule.provideActiveDownloads())
        chunksCacheDirFile = testModule.provideChunksCacheDirFile()

        chunkPersister = ChunkPersister(
            fileManager,
            cacheHandler,
            activeDownloads,
            false
        )
    }

    @After
    fun tearDown() {
        cacheHandler.clearCache()
        activeDownloads.clear()
    }

    @Test
    fun `test try store two chunks one chunk fails`() {
        val url = "http://testUrl.com/123.jpg".toHttpUrl()
        val fileBytes = javaClass.classLoader!!.getResourceAsStream("test_img1.jpg").readBytes()
        val chunksCount = 2
        val chunks = chunkLong(fileBytes.size.toLong(), chunksCount, MIN_CHUNK_SIZE)
        val chunkResponses = createChunkResponses(url, chunks, fileBytes)
        val output = cacheHandler.getOrCreateCacheFile(url) as RawFile
        val request = createFileDownloadRequest(url, chunksCount, file = output)
        val chunkIndex = AtomicInteger(0)
        val timesCalled = AtomicInteger(0)

        activeDownloads.put(url, request)

        doAnswer { invocationOnMock ->
            val chunkIndexParam = invocationOnMock.getArgument<Int>(1)

            if (chunkIndexParam == 1 && timesCalled.getAndIncrement() == 5) {
                throw IOException("BAM!!!")
            }
        }
            .whenever(activeDownloads)
            .updateDownloaded(anyString().toHttpUrl(), anyInt(), anyLong())

        val testObserver = Flowable.fromIterable(chunkResponses)
            .observeOn(Schedulers.newThread())
            .flatMap { chunkResponse ->
                chunkPersister.storeChunkInFile(
                    url,
                    chunkResponse,
                    AtomicLong(),
                    chunkIndex.getAndIncrement(),
                    chunksCount
                )
            }
            .test()

        val (events, errors, completes) = testObserver
            .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
            .events

        assertTrue(completes.isEmpty())
        assertEquals(1, errors.size)
        assertTrue(errors.first() is IOException)
        assertEquals("BAM!!!", (errors.first() as IOException).message)

        val successEvent = events.first { event -> event is ChunkDownloadEvent.ChunkSuccess }
        successEvent as ChunkDownloadEvent.ChunkSuccess
        assertEquals(1, fileManager.listFiles(chunksCacheDirFile).size)

        val fileName = fileManager.getName(successEvent.chunkCacheFile)
        val fileSize = fileManager.getLength(successEvent.chunkCacheFile)

        val chunkString = String.format("%d_%d", successEvent.chunk.start, successEvent.chunk.end)
        assertTrue(fileName.contains(chunkString))
        assertEquals(fileSize, successEvent.chunk.chunkSize())

        chunkResponses.forEach { chunkResponse -> chunkResponse.response.closeQuietly() }
    }

    @Test
    fun `test store two chunks on the disk both succeed`() {
        val url = "http://testUrl.com/123.jpg".toHttpUrl()
        val fileBytes = javaClass.classLoader!!.getResourceAsStream("test_img1.jpg").readBytes()
        val chunksCount = 2
        val chunks = chunkLong(fileBytes.size.toLong(), chunksCount, MIN_CHUNK_SIZE)
        val chunkResponses = createChunkResponses(url, chunks, fileBytes)
        val output = cacheHandler.getOrCreateCacheFile(url) as RawFile
        val request = createFileDownloadRequest(url, chunksCount, file = output)
        val chunkIndex = AtomicInteger(0)
        activeDownloads.put(url, request)

        val testObserver = Flowable.fromIterable(chunkResponses)
            .flatMap { chunkResponse ->
                chunkPersister.storeChunkInFile(
                    url,
                    chunkResponse,
                    AtomicLong(),
                    chunkIndex.getAndIncrement(),
                    chunksCount
                ).subscribeOn(Schedulers.newThread())
            }.test()

        val (events, errors, completes) = testObserver
            .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
            .events

        assertTrue(errors.isEmpty())
        assertEquals(1, completes.size)

        val successEventsGrouped = events
            .filterIsInstance<ChunkDownloadEvent.ChunkSuccess>()
            .groupBy { event -> event.chunkIndex }

        val progressEventsGrouped = events
            .filterIsInstance<ChunkDownloadEvent.Progress>()
            .groupBy { event -> event.chunkIndex }

        assertEquals(2, successEventsGrouped.values.size)
        successEventsGrouped.forEach { (_, chunkSuccessEvents) ->
            assertEquals(1, chunkSuccessEvents.size)
            val chunkSuccessEvent = chunkSuccessEvents.first()

            val start = chunkSuccessEvent.chunk.start.toInt()
            val end = chunkSuccessEvent.chunk.realEnd.toInt()

            val expectedBytes = fileBytes.sliceArray(start until end)
            val actualBytes = fileManager.getInputStream(chunkSuccessEvent.chunkCacheFile)!!.use {
                it.readBytes()
            }

            assertArrayEquals(expectedBytes, actualBytes)
        }

        assertEquals(30, progressEventsGrouped.values.map { it.size }.sum())
        progressEventsGrouped.forEach { (chunkIndex, chunkProgressEvents) ->
            chunkProgressEvents.zipWithNext().forEach { (current, next) ->
                assertEquals(chunkIndex, current.chunkIndex)
                assertEquals(chunkIndex, next.chunkIndex)

                assertTrue(current.chunkSize == next.chunkSize)
                val chunkSize = current.chunkSize

                assertTrue(current.downloaded < next.downloaded)
                assertTrue(current.downloaded <= chunkSize)
                assertTrue(next.downloaded <= chunkSize)
            }
        }

        chunkResponses.forEach { chunkResponse -> chunkResponse.response.closeQuietly() }
    }

    @Test
    fun `test server returns 404 for a chunk`() {
        val url = "http://testUrl.com/123.jpg".toHttpUrl()
        val chunksCount = 1
        val chunkResponse = create404ChunkResponse(url)
        val output = cacheHandler.getOrCreateCacheFile(url) as RawFile
        val request = createFileDownloadRequest(url, chunksCount, file = output)
        activeDownloads.put(url, request)

        val testObserver = chunkPersister.storeChunkInFile(
            url,
            chunkResponse,
            AtomicLong(),
            0,
            chunksCount
        )
            .subscribeOn(Schedulers.newThread())
            .test()

        val (events, errors, completes) = testObserver
            .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
            .events

        assertFalse(errors.isEmpty())
        assertTrue(events.isEmpty())
        assertTrue(completes.isEmpty())

        val error = errors.first()
        assertTrue(error is FileCacheException.FileNotFoundOnTheServerException)
    }

    private fun create404ChunkResponse(url: HttpUrl): ChunkResponse {
        val request = with(Request.Builder()) {
            url(url)
            build()
        }

        val badResponse = with(Response.Builder()) {
            request(request)
            protocol(Protocol.HTTP_1_1)
            code(404)
            message("")
            build()
        }

        return ChunkResponse(Chunk.wholeFile(), badResponse)
    }

    private fun createChunkResponses(
        url: HttpUrl,
        chunks: List<Chunk>,
        fileBytes: ByteArray
    ): List<ChunkResponse> {
        return chunks.map { chunk ->
            val chunkBytes = fileBytes.sliceArray(chunk.start.toInt() until chunk.realEnd.toInt())
            val response = createResponse(url, chunkBytes)

            return@map ChunkResponse(chunk, response)
        }
    }

    private fun createResponse(url: HttpUrl, fileBytes: ByteArray): Response {
        val request = with(Request.Builder()) {
            url(url)
            build()
        }

        return with(Response.Builder()) {
            request(request)
            protocol(Protocol.HTTP_1_1)
            code(206)
            message("")
            body(
                fileBytes
                    .toResponseBody("image/jpg".toMediaType())
            )

            build()
        }
    }

    companion object {
        private const val MAX_AWAIT_TIME_SECONDS = 5L
    }
}