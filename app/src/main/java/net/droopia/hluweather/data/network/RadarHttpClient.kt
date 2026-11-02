package net.droopia.hluweather.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json

/** All actual radar HTTP exchanges must be individually admitted by RadarNetworkScheduler. */
internal fun createRadarHttpClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
            // OkHttp can still follow up a 503 Retry-After: 0 or a coalesced HTTP/2 421 even
            // with recovery disabled. Give each call one marker; reject a second exchange before
            // it sends bytes, so only the scheduler can admit the next network attempt.
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .tag(RadarHttpAttempt::class.java, RadarHttpAttempt())
                    .build()
                chain.proceed(request)
            }
            addNetworkInterceptor { chain ->
                val attempt = chain.request().tag(RadarHttpAttempt::class.java)
                    ?: throw IOException("Missing radar attempt marker")
                if (!attempt.started.compareAndSet(false, true)) {
                    throw IOException("Automatic radar HTTP follow-up was blocked")
                }
                chain.proceed(chain.request())
            }
        }
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    expectSuccess = false
    install(HttpTimeout) { requestTimeoutMillis = 15_000 }
}

private class RadarHttpAttempt {
    val started = AtomicBoolean(false)
}
