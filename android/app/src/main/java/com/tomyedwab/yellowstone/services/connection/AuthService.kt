package com.tomyedwab.yellowstone.services.connection

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UnauthenticatedError(message: String) : Exception(message)

class AuthService(private val allowInsecureConnections: Boolean = false) {
    private val client = createOkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    private fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Set 60 second timeouts for all operations
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)

        if (allowInsecureConnections) {
            // Create a trust manager that accepts all certificates
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    private fun getRefreshTokenFromCookie(response: Response): String {
        // Extract YRT cookie value as refresh token
        val cookies = response.headers("Set-Cookie")
        for (cookie in cookies) {
            if (cookie.startsWith("YRT=")) {
                val refreshToken = cookie.substringAfter("YRT=").substringBefore(";")
                if (refreshToken.isNotEmpty()) {
                    return refreshToken
                }
            }
        }
        throw UnauthenticatedError("No YRT cookie found in response")
    }

    suspend fun doLogin(url: String, username: String, password: String): String {
        return withContext(Dispatchers.IO) {
            val loginData = JsonObject().apply {
                addProperty("username", username)
                addProperty("password", password)
            }

            val requestBody = gson.toJson(loginData).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$url/public/login")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    return@withContext getRefreshTokenFromCookie(it)
                } else {
                    val error = it.body?.string() ?: "Unknown error"
                    throw UnauthenticatedError("Error logging in: $error")
                }
            }
        }
    }

    suspend fun refreshAccessToken(url: String, refreshToken: String): Pair<String, String> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$url/public/access_token")
                .post("".toRequestBody())
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "YRT=$refreshToken")
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val responseData = gson.fromJson(it.body?.string(), JsonObject::class.java)
                    val accessToken = responseData.get("access_token")?.asString
                    val newRefreshToken = getRefreshTokenFromCookie(it)
                    if (accessToken != null) {
                        return@withContext accessToken to newRefreshToken
                    }
                }
                throw UnauthenticatedError("Failed to refresh token: " + response.body?.string())
            }
        }
    }

    suspend fun fetchAuthenticated(
        url: String,
        refreshToken: String,
        accessToken: String,
        method: String = "GET",
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap()
    ): Response {
        return withContext(Dispatchers.IO) {
            var currentAccessToken = accessToken

            // If no access token, refresh it first
            if (currentAccessToken.isEmpty()) {
                throw UnauthenticatedError("No access token provided")
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $currentAccessToken")

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            when (method.uppercase()) {
                "POST" -> requestBuilder.post(body ?: "".toRequestBody())
                "PUT" -> requestBuilder.put(body ?: "".toRequestBody())
                "DELETE" -> requestBuilder.delete(body)
                else -> requestBuilder.get()
            }

            var response = client.newCall(requestBuilder.build()).execute()

            // If unauthorized, raise exception to clear the access token & re-auth
            if (response.code == 401) {
                throw UnauthenticatedError("Invalid access token")
            }

            return@withContext response
        }
    }
}
