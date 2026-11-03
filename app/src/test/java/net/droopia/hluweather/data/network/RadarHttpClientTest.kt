package net.droopia.hluweather.data.network

import io.ktor.client.request.get
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.droopia.hluweather.data.model.RadarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarHttpClientTest {
    @Test
    fun eightSameHostRequestsReachServerAndNinthWaitsForCapacity() = runBlocking {
        HeldResponseLocalHttpServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val scheduler = RadarNetworkScheduler(scope)
            val client = createRadarHttpClient()
            try {
                val firstEight = (1..8).map {
                    async(Dispatchers.IO) {
                        scheduler.execute(RadarProvider.RAINVIEWER) { client.get(server.url) }
                    }
                }

                assertTrue("All eight requests should reach the held-response server",
                    server.awaitEightRequests(5_000))
                assertEquals(8, server.requests.get())
                assertTrue("Responses are held until the concurrency assertion", firstEight.none { it.isCompleted })

                // UNDISPATCHED runs through scheduler admission before suspending on its ninth slot.
                val ninth = async(start = CoroutineStart.UNDISPATCHED) {
                    scheduler.execute(RadarProvider.RAINVIEWER) { client.get(server.url) }
                }
                assertFalse("The ninth request must wait while all eight are active", ninth.isCompleted)
                assertEquals(8, server.requests.get())

                server.releaseResponses()
                val responses = withTimeout(5_000) { (firstEight + ninth).awaitAll() }
                assertTrue(responses.all { it.status.value == 200 })
                assertEquals("The waiting request reaches the same host after capacity is released", 9,
                    server.requests.get())
            } finally {
                server.releaseResponses()
                client.close()
                scope.cancel()
            }
        }
    }

    @Test
    fun okhttpCannotRetryA503OutsideTheSharedBudget() = runBlocking {
        LocalHttpServer { n -> if (n == 1) "503 Service Unavailable\r\nRetry-After: 0" else "200 OK" }.use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val clock = AtomicLong(0)
            val scheduler = RadarNetworkScheduler(scope, clock::get, clock::get)
            val client = createRadarHttpClient()
            try {
                val first = runCatching { scheduler.execute(RadarProvider.RAINVIEWER) { client.get(server.url) } }
                assertTrue("OkHttp's internal follow-up must be blocked",
                    generateSequence(first.exceptionOrNull()) { it.cause }
                        .any { it.message?.contains("Automatic radar HTTP follow-up was blocked") == true })
                assertEquals("Only the budgeted HTTP exchange reached the server", 1, server.requests.get())
                val second = scheduler.execute(RadarProvider.RAINVIEWER) { client.get(server.url) }
                assertEquals(200, second.status.value)
                assertEquals(2, server.requests.get())
                repeat(88) { scheduler.execute(RadarProvider.RAINVIEWER) {} }
                val blocked = async { scheduler.execute(RadarProvider.RAINVIEWER) { "admitted" } }
                delay(100)
                assertFalse("Both real HTTP calls must count toward the 90-attempt window", blocked.isCompleted)
                clock.set(60_000)
                scheduler.priorityChanged(RadarRequestPriority(false))
                assertEquals("admitted", withTimeout(5_000) { blocked.await() })
            } finally {
                client.close()
                scope.cancel()
            }
        }
    }

    @Test
    fun redirectsAreNotFollowedAnd429HeadersReachTheCaller() = runBlocking {
        LocalHttpServer { n ->
            if (n == 1) "302 Found\r\nLocation: /redirected" else "429 Too Many Requests\r\nRetry-After: 17"
        }.use { server ->
            val client = createRadarHttpClient()
            try {
                assertEquals(302, client.get(server.url).status.value)
                assertEquals(1, server.requests.get())
                val limited = client.get(server.url)
                assertEquals(429, limited.status.value)
                assertEquals("17", limited.headers["Retry-After"])
                assertEquals(2, server.requests.get())
            } finally {
                client.close()
            }
        }
    }

    private class LocalHttpServer(private val response: (Int) -> String) : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}/radar"
        val requests = AtomicInteger()
        private val worker = thread(name = "radar-http-follow-up-test", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().bufferedReader()
                        // A blocked follow-up can open a socket, but must send no HTTP request.
                        input.readLine() ?: return@use
                        while (!input.readLine().isNullOrEmpty()) Unit
                        val status = response(requests.incrementAndGet())
                        socket.getOutputStream().write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                } catch (error: Exception) {
                    if (!server.isClosed) throw error
                }
            }
        }

        override fun close() {
            server.close()
            worker.join(5_000)
        }
    }

    private class HeldResponseLocalHttpServer : AutoCloseable {
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val release = CountDownLatch(1)
        private val firstEightRequests = CountDownLatch(8)
        private val sockets = ConcurrentHashMap.newKeySet<Socket>()
        private val requestWorkers = CopyOnWriteArrayList<Thread>()
        val url = "http://127.0.0.1:${server.localPort}/radar"
        val requests = AtomicInteger()

        private val acceptWorker = thread(name = "radar-http-concurrency-test", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    sockets.add(socket)
                    requestWorkers += thread(name = "radar-http-concurrency-request", isDaemon = true) {
                        try {
                            socket.use {
                                it.soTimeout = 5_000
                                val input = it.getInputStream().bufferedReader()
                                if (input.readLine() == null) return@use
                                while (!input.readLine().isNullOrEmpty()) Unit
                                requests.incrementAndGet()
                                firstEightRequests.countDown()
                                release.await()
                                it.getOutputStream().apply {
                                    write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                    flush()
                                }
                            }
                        } catch (error: Exception) {
                            if (!server.isClosed) throw error
                        } finally {
                            sockets.remove(socket)
                        }
                    }
                } catch (error: Exception) {
                    if (!server.isClosed) throw error
                }
            }
        }

        fun awaitEightRequests(timeoutMillis: Long): Boolean =
            firstEightRequests.await(timeoutMillis, TimeUnit.MILLISECONDS)

        fun releaseResponses() {
            release.countDown()
        }

        override fun close() {
            releaseResponses()
            server.close()
            sockets.forEach { runCatching { it.close() } }
            acceptWorker.join(5_000)
            requestWorkers.forEach { it.join(5_000) }
        }
    }
}
