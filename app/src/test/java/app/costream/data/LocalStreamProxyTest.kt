package app.costream.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class LocalStreamProxyTest {

    @Test
    fun refererMatchesDocumentation() {
        assertEquals("https://moviebox.ph/", LocalStreamProxy.refererForUrl("https://bcdnxw.hakunaymatata.com/video.mp4"))
        assertEquals("https://mixdrop.top/", LocalStreamProxy.refererForUrl("https://cdn.mxcontent.net/file.mp4"))
        assertEquals("https://netmirror.hair/", LocalStreamProxy.refererForUrl("https://s1.messycloud.ink/video.mp4"))
        assertEquals("https://www.messycloud.ink/", LocalStreamProxy.refererForUrl("https://cdn.juicybits.io/video.mp4"))
        assertEquals("https://videodownloader.site/", LocalStreamProxy.refererForUrl("https://pbcdn.aoneroom.com/video.mp4"))
    }

    @Test
    fun proxiesRangeRequestsAndPreservesPartialContent() {
        val capturedRange = AtomicReference<String?>()
        val capturedReferer = AtomicReference<String?>()

        val upstreamServer = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        val upstreamPort = upstreamServer.localPort
        val serverThread = Thread {
            upstreamServer.accept().use { sock ->
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        capturedRange.set(line.substringAfter(":").trim())
                    } else if (line.startsWith("Referer:", ignoreCase = true)) {
                        capturedReferer.set(line.substringAfter(":").trim())
                    }
                }
                val payload = "0123456789".toByteArray()
                val headers = "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Type: video/mp4\r\n" +
                    "Content-Range: bytes 0-9/100\r\n" +
                    "Content-Length: ${payload.size}\r\n" +
                    "Accept-Ranges: bytes\r\n\r\n"
                sock.getOutputStream().write(headers.toByteArray())
                sock.getOutputStream().write(payload)
                sock.getOutputStream().flush()
            }
        }.apply { isDaemon = true; start() }

        try {
            val client = OkHttpClient()
            val proxy = LocalStreamProxy(client)
            val targetUrl = "http://127.0.0.1:$upstreamPort/movie.mp4"
            val proxiedUrl = proxy.proxyUrl(targetUrl, mapOf("Referer" to "https://moviebox.ph/"))
            assertTrue(proxiedUrl.startsWith("http://127.0.0.1:"))

            val request = Request.Builder()
                .url(proxiedUrl)
                .header("Range", "bytes=0-9")
                .build()
            client.newCall(request).execute().use { response ->
                assertEquals(206, response.code)
                assertEquals("bytes 0-9/100", response.header("Content-Range"))
                assertEquals("video/mp4", response.header("Content-Type"))
                assertEquals("0123456789", response.body?.string())
            }
            assertEquals("bytes=0-9", capturedRange.get())
            assertEquals("https://moviebox.ph/", capturedReferer.get())
        } finally {
            upstreamServer.close()
            serverThread.join(1000)
        }
    }
}
