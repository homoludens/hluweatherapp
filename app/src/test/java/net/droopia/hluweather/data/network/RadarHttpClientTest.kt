package net.droopia.hluweather.data.network

import io.ktor.client.request.get
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
}
