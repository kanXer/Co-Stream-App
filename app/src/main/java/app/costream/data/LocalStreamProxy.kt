package app.costream.data

import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local Range-preserving HTTP/1.1 streaming proxy backed by OkHttp connection pooling.
 * Injects required CDN Referer/User-Agent headers and retries transient 429/5xx responses
 * so ExoPlayer gets fast, pooled HTTP/2+TLS upstream connections without Range header loss.
 */
class LocalStreamProxy(baseClient: OkHttpClient) {

    private val streamClient: OkHttpClient = baseClient.newBuilder()
        .cache(null)
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val headerStore = ConcurrentHashMap<String, Map<String, String>>()
    private val executor: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "costream-proxy-worker").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var port: Int = -1

    @Synchronized
    fun ensureStarted(): Int {
        if (port > 0 && serverSocket?.isClosed == false) return port
        val socket = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort
        Thread({
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (_: IOException) {
                    if (socket.isClosed) break
                }
            }
        }, "costream-proxy-accept").apply {
            isDaemon = true
            start()
        }
        return port
    }

    /**
     * Returns a local proxy URL for HTTP/HTTPS media or subtitle targets.
     * Local file:// URIs and already-proxied URLs are returned unchanged.
     */
    fun proxyUrl(targetUrl: String, headers: Map<String, String> = emptyMap()): String {
        if (!targetUrl.startsWith("http://", ignoreCase = true) &&
            !targetUrl.startsWith("https://", ignoreCase = true)
        ) {
            return targetUrl
        }
        val activePort = ensureStarted()
        if (targetUrl.startsWith("http://127.0.0.1:$activePort/")) return targetUrl
        if (headers.isNotEmpty()) {
            if (headerStore.size > 256) headerStore.clear()
            headerStore[targetUrl] = headers
        }
        val encoded = URLEncoder.encode(targetUrl, "UTF-8")
        return "http://127.0.0.1:$activePort/stream?url=$encoded"
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 30_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            if (method != "GET" && method != "HEAD") {
                writeSimpleError(sock, 405, "Method Not Allowed")
                return
            }

            val incomingHeaders = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    incomingHeaders[key] = value
                }
            }

            val targetUrl = extractUrlParam(parts[1])
            if (targetUrl.isNullOrBlank() ||
                (!targetUrl.startsWith("http://", ignoreCase = true) &&
                    !targetUrl.startsWith("https://", ignoreCase = true))
            ) {
                writeSimpleError(sock, 400, "Bad Request")
                return
            }

            val storedHeaders = headerStore[targetUrl].orEmpty()
            val upstreamHeaders = buildUpstreamHeaders(targetUrl, storedHeaders, incomingHeaders["range"])

            val upstreamResponse = executeWithRetry(method, targetUrl, upstreamHeaders)
            if (upstreamResponse == null) {
                runCatching { android.util.Log.w("LocalStreamProxy", "502 Bad Gateway for $targetUrl") }
                writeSimpleError(sock, 502, "Bad Gateway")
                return
            }

            runCatching { android.util.Log.d("LocalStreamProxy", "$method $targetUrl -> ${upstreamResponse.code} range=${incomingHeaders["range"]}") }

            upstreamResponse.use { resp ->
                val out = BufferedOutputStream(sock.getOutputStream(), BUFFER_SIZE)
                val statusCode = resp.code
                val statusMsg = resp.message.ifBlank { defaultReason(statusCode) }
                val headerBuilder = StringBuilder()
                headerBuilder.append("HTTP/1.1 $statusCode $statusMsg\r\n")
                resp.header("Content-Type")?.let { headerBuilder.append("Content-Type: $it\r\n") }
                resp.header("Content-Length")?.let { headerBuilder.append("Content-Length: $it\r\n") }
                resp.header("Content-Range")?.let { headerBuilder.append("Content-Range: $it\r\n") }
                headerBuilder.append("Accept-Ranges: ${resp.header("Accept-Ranges") ?: "bytes"}\r\n")
                headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
                headerBuilder.append("Connection: close\r\n\r\n")
                out.write(headerBuilder.toString().toByteArray(Charsets.ISO_8859_1))

                if (method == "GET") {
                    val bodyStream = resp.body?.byteStream()
                    if (bodyStream != null) {
                        val bufferedIn = BufferedInputStream(bodyStream, BUFFER_SIZE)
                        val buf = ByteArray(BUFFER_SIZE)
                        try {
                            while (true) {
                                val read = bufferedIn.read(buf)
                                if (read == -1) break
                                out.write(buf, 0, read)
                            }
                        } catch (_: IOException) {
                            // Client closed socket early (e.g. ExoPlayer seek/cancel) - expected.
                        }
                    }
                }
                runCatching { out.flush() }
            }
        }
    }

    private fun executeWithRetry(
        method: String,
        url: String,
        headers: Map<String, String>
    ): Response? {
        repeat(3) { attempt ->
            val builder = Request.Builder().url(url)
            if (method == "HEAD") builder.head() else builder.get()
            headers.forEach { (k, v) -> builder.header(k, v) }
            val resp = runCatching { streamClient.newCall(builder.build()).execute() }.getOrNull()
            if (resp != null) {
                if (resp.isSuccessful || resp.code == 206) return resp
                if (resp.code != 429 && resp.code < 500) return resp
                resp.close()
            }
            if (attempt < 2) {
                runCatching { Thread.sleep(250L * (attempt + 1)) }
            }
        }
        return null
    }

    private fun buildUpstreamHeaders(
        targetUrl: String,
        storedHeaders: Map<String, String>,
        rangeHeader: String?
    ): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        merged["Accept"] = "*/*"
        merged["User-Agent"] = storedHeaders["User-Agent"] ?: NetMirrorRepository.USER_AGENT
        val referer = storedHeaders["Referer"] ?: refererForUrl(targetUrl)
        merged["Referer"] = referer
        storedHeaders.forEach { (k, v) ->
            if (!k.equals("Host", true) && !k.equals("Range", true)) {
                merged[k] = v
            }
        }
        val host = targetUrl.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        if (host.contains("net52.cc") && !merged.containsKey("Cookie")) {
            merged["Cookie"] = "ott=nf"
        }
        if (!rangeHeader.isNullOrBlank()) {
            merged["Range"] = rangeHeader
        }
        return merged
    }

    private fun extractUrlParam(requestTarget: String): String? {
        val qIndex = requestTarget.indexOf('?')
        if (qIndex < 0) return null
        val query = requestTarget.substring(qIndex + 1)
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "url") {
                return URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
            }
        }
        return null
    }

    private fun writeSimpleError(socket: Socket, code: Int, message: String) {
        val response = "HTTP/1.1 $code $message\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        runCatching {
            socket.getOutputStream().write(response.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().flush()
        }
    }

    private fun defaultReason(code: Int): String = when (code) {
        200 -> "OK"
        206 -> "Partial Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        416 -> "Range Not Satisfiable"
        429 -> "Too Many Requests"
        502 -> "Bad Gateway"
        else -> "Status $code"
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        fun refererForUrl(url: String): String {
            val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
            return when {
                host.endsWith("hakunaymatata.com") -> "https://moviebox.ph/"
                host.endsWith("mxcontent.net") -> "https://mixdrop.top/"
                host.endsWith("messycloud.ink") -> "https://netmirror.hair/"
                host.endsWith("juicybits.io") -> "https://www.messycloud.ink/"
                host.endsWith("aoneroom.com") -> "https://videodownloader.site/"
                host.contains("net52.cc") || host.contains("freecdn") || host.endsWith(".top") -> "https://net52.cc/"
                else -> "https://net79.cc/"
            }
        }
    }
}
