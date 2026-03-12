package com.novel.writing.assistant.network

import android.content.Context
import android.os.Build
import com.novel.writing.assistant.BuildConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "network_resolver"
    private const val KEY_LAST_OCTET = "last_octet"
    private const val KEY_LAST_PORT = "last_port"
    private const val HEALTH_PATH = "/v1/health"
    private const val DEFAULT_PORT = 8080
    private const val MAX_CONCURRENCY = 24
    private const val CONNECT_TIMEOUT_MS = 350
    private const val READ_TIMEOUT_MS = 350

    private val configuredBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var cachedBaseUrl: String? = null
    @Volatile
    private var cachedPrefix: String? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun getBaseUrl(): String {
        val configured = configuredBaseUrl
        val context = appContext ?: return configured
        val parsed = runCatching { URI(configured) }.getOrNull() ?: return configured
        val host = parsed.host ?: return configured
        if (!isPrivateHost(host) || isEmulator()) return configured

        val ip = getCurrentIpv4Address() ?: return configured
        val prefix = ip.substringBeforeLast('.', "")
        if (prefix.isBlank()) return configured

        val cached = cachedBaseUrl
        if (!cached.isNullOrBlank() && cachedPrefix == prefix) {
            return cached
        }

        val port = if (parsed.port > 0) parsed.port else DEFAULT_PORT
        val apiPath = normalizeApiPath(parsed.path)
        val remembered = getRememberedBaseUrl(context, prefix, port, apiPath)
        val discovered = remembered ?: discoverLanBaseUrl(prefix, port, apiPath)
        val selected = discovered ?: configured

        if (!discovered.isNullOrBlank()) {
            rememberBaseUrl(context, discovered)
        }

        cachedBaseUrl = selected.trimEnd('/')
        cachedPrefix = prefix
        return cachedBaseUrl!!
    }

    val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.SECONDS)
                callTimeout(320, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) {
            level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
        }
    }

    private fun normalizeApiPath(path: String?): String {
        val normalized = path?.trim()?.ifBlank { "/api" } ?: "/api"
        return if (normalized.startsWith("/")) normalized.trimEnd('/') else "/${normalized.trimEnd('/')}"
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val nums = parts.mapNotNull { it.toIntOrNull() }
        if (nums.size != 4) return false
        return nums[0] == 10 ||
            (nums[0] == 172 && nums[1] in 16..31) ||
            (nums[0] == 192 && nums[1] == 168)
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }

    private fun getCurrentIpv4Address(): String? {
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull() ?: return null
        interfaces.forEach { networkInterface ->
            if (!networkInterface.isUp || networkInterface.isLoopback) return@forEach
            val addresses = Collections.list(networkInterface.inetAddresses)
            addresses.forEach { address ->
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress
                }
            }
        }
        return null
    }

    private fun getRememberedBaseUrl(context: Context, prefix: String, port: Int, apiPath: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val octet = prefs.getInt(KEY_LAST_OCTET, -1)
        val rememberedPort = prefs.getInt(KEY_LAST_PORT, port)
        if (octet !in 1..254) return null
        val baseUrl = buildBaseUrl(prefix, octet, rememberedPort, apiPath)
        return if (isHealthy(baseUrl)) baseUrl else null
    }

    private fun rememberBaseUrl(context: Context, baseUrl: String) {
        val parsed = runCatching { URI(baseUrl) }.getOrNull() ?: return
        val host = parsed.host ?: return
        val octet = host.substringAfterLast('.', "").toIntOrNull() ?: return
        val port = if (parsed.port > 0) parsed.port else DEFAULT_PORT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_OCTET, octet)
            .putInt(KEY_LAST_PORT, port)
            .apply()
    }

    private suspend fun discoverLanBaseUrl(prefix: String, port: Int, apiPath: String): String? = coroutineScope {
        val candidates = candidateOctets()
        val found = CompletableDeferred<String?>()
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val jobs = candidates.map { octet ->
            launch(Dispatchers.IO) {
                if (found.isCompleted) return@launch
                semaphore.withPermit {
                    if (found.isCompleted) return@withPermit
                    val baseUrl = buildBaseUrl(prefix, octet, port, apiPath)
                    if (isHealthy(baseUrl)) {
                        found.complete(baseUrl)
                    }
                }
            }
        }
        launch {
            jobs.forEach { it.join() }
            if (!found.isCompleted) {
                found.complete(null)
            }
        }
        found.await()
    }

    private fun candidateOctets(): List<Int> {
        val prioritized = mutableListOf<Int>()
        prioritized += 100..130
        prioritized += 2..40
        prioritized += 131..200
        prioritized += 201..254
        prioritized += 41..99
        return prioritized.distinct()
    }

    private fun buildBaseUrl(prefix: String, octet: Int, port: Int, apiPath: String): String {
        val p = if (port > 0) port else DEFAULT_PORT
        return "http://$prefix.$octet:$p$apiPath".trimEnd('/')
    }

    private fun isHealthy(baseUrl: String): Boolean {
        val healthUrl = "$baseUrl$HEALTH_PATH"
        return runCatching {
            val connection = URI(healthUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doInput = true
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }
}
