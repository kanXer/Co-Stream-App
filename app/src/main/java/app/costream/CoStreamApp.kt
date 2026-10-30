package app.costream

import android.app.Application
import app.costream.data.AppStore
import app.costream.data.AuthRepository
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

    override fun onCreate() {
        super.onCreate()

        val httpClient = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_cache"), 32L * 1024L * 1024L))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .callTimeout(50, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Co-Stream/1.0 (Android)")
                        .build()
                )
            }
            .build()

        netMirrorRepository = NetMirrorRepository(httpClient)
        authRepository = AuthRepository(this)
        store = AppStore(this)
    }
}
