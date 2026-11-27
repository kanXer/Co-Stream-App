package app.costream

import android.app.Application
import app.costream.data.AppStore
import app.costream.data.AuthRepository
import app.costream.data.DohDns
import app.costream.data.LocalStreamProxy
import app.costream.data.NetMirrorRepository
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class CoStreamApp : Application() {

    lateinit var netMirrorRepository: NetMirrorRepository
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var store: AppStore
        private set
    lateinit var streamProxy: LocalStreamProxy
        private set

    override fun onCreate() {
        super.onCreate()

        val apiDispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        }

        val httpClient = OkHttpClient.Builder()
            .dns(DohDns())
            .dispatcher(apiDispatcher)
            .cache(Cache(File(cacheDir, "http_cache"), 32L * 1024L * 1024L))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val ua = request.header("User-Agent") ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", ua)
                        .build()
                )
            }
            .build()

        streamProxy = LocalStreamProxy(httpClient).apply { ensureStarted() }
        netMirrorRepository = NetMirrorRepository(httpClient, this)
        authRepository = AuthRepository(this)
        store = AppStore(this)
    }
}
