package app.costream.data

import android.content.Context
import app.costream.BuildConfig
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AuthRepository(private val appContext: Context) {

    private val credentialManager by lazy { CredentialManager.create(appContext) }
    val webClientId: String? by lazy {
        BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: readClientId()
    }
    val googleAvailable: Boolean get() = !webClientId.isNullOrBlank()

    suspend fun signInWithGoogle(activityContext: Context): AuthUser {
        val clientId = webClientId ?: error("Add app/src/main/assets/creds.json to enable Google sign-in.")
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = credentialManager.getCredential(activityContext, request)
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a valid account."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        return AuthUser(
            id = google.id,
            name = google.displayName?.takeIf { it.isNotBlank() } ?: google.id.substringBefore('@'),
            email = google.id,
            photoUrl = google.profilePictureUri?.toString(),
            guest = false
        )
    }

    suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun readClientId(): String? = runCatching {
        val text = appContext.assets.open("creds.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        root.optString("web_client_id").takeIf { it.isNotBlank() }
            ?: root.optString("server_client_id").takeIf { it.isNotBlank() }
            ?: root.optJSONObject("web")?.optString("client_id")?.takeIf { it.isNotBlank() }
            ?: root.optJSONObject("installed")?.optString("client_id")?.takeIf { it.isNotBlank() }
            ?: findGoogleServicesWebClient(root)
    }.getOrNull()

    private fun findGoogleServicesWebClient(root: JSONObject): String? {
        val clients = root.optJSONArray("client") ?: return null
        for (index in 0 until clients.length()) {
            val oauth = clients.optJSONObject(index)?.optJSONArray("oauth_client") ?: continue
            for (oauthIndex in 0 until oauth.length()) {
                val entry = oauth.optJSONObject(oauthIndex) ?: continue
                if (entry.optInt("client_type") == 3) {
                    return entry.optString("client_id").takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }
}
