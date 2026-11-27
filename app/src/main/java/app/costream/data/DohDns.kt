package app.costream.data

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS (Cloudflare 1.1.1.1 / Google 8.8.8.8) resolver with in-memory caching
 * to bypass carrier/ISP DNS sinkholes on streaming & catalog domains.
 */
class DohDns : Dns {

    private val dohClient = OkHttpClient.Builder()
        .dns(Dns.SYSTEM)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == "127.0.0.1" || hostname == "localhost") {
            return Dns.SYSTEM.lookup(hostname)
        }
        cache[hostname]?.let { (expiresAt, addresses) ->
            if (System.currentTimeMillis() < expiresAt && addresses.isNotEmpty()) {
                return addresses
            }
        }

        val resolved = queryDoh("https://1.1.1.1/dns-query?name=$hostname&type=A")
            ?: queryDoh("https://dns.google/resolve?name=$hostname&type=A")
            ?: FALLBACK_HOSTS[hostname.lowercase()]?.mapNotNull { ip ->
                runCatching { InetAddress.getByName(ip) }.getOrNull()
            }

        if (!resolved.isNullOrEmpty()) {
            cache[hostname] = (System.currentTimeMillis() + CACHE_TTL_MS) to resolved
            return resolved
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun queryDoh(url: String): List<InetAddress>? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()
            dohClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val answers = JSONObject(body).optJSONArray("Answer") ?: return@runCatching null
                val addrs = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val ans = answers.optJSONObject(i) ?: continue
                    if (ans.optInt("type") == 1) {
                        val ip = ans.optString("data")
                        if (ip.isNotBlank()) {
                            runCatching { InetAddress.getByName(ip) }.getOrNull()?.let(addrs::add)
                        }
                    }
                }
                addrs.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private val FALLBACK_HOSTS = mapOf(
            "net79.cc" to listOf("172.67.215.149", "104.21.16.197"),
            "net27.cc" to listOf("172.67.146.66", "104.21.81.201")
        )
    }
}
