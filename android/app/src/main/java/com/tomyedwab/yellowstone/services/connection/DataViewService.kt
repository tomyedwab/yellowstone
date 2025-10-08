package com.tomyedwab.yellowstone.services.connection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.provider.connection.HubConnectionState
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

data class DataViewResult<T>(
    val loading: Boolean,
    val data: T? = null,
    val error: String? = null
)

data class DataViewCacheEntry(
    val data: Any?,
    val refCount: Int,
    val expiry: Long?
)

class DataViewService(private val authService: AuthService) {
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, DataViewCacheEntry>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<Any?>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun <T> createDataView(
        connectionState: LiveData<HubConnectionState>,
        componentName: String,
        apiPath: String,
        apiParams: Map<String, String> = emptyMap(),
        typeToken: TypeToken<T>
    ): LiveData<DataViewResult<T>> {
        
        val result = MediatorLiveData<DataViewResult<T>>()
        
        result.addSource(connectionState) { state ->
            if (state !is HubConnectionState.Connected) {
                result.value = DataViewResult(loading = true, data = null)
                return@addSource
            }

            val instanceId = state.backendComponentIDs.componentMap[componentName]
            if (instanceId == null) {
                result.value = DataViewResult(
                    loading = false, 
                    data = null, 
                    error = "Component not found: $componentName"
                )
                return@addSource
            }

            val currentEventId = state.backendEventIDs[instanceId]
            if (currentEventId == null || currentEventId == -1) {
                result.value = DataViewResult(loading = false, data = null)
                return@addSource
            }

            // Build query key
            var queryKey = "$apiPath@$currentEventId"
            apiParams.forEach { (key, value) ->
                queryKey += "&$key=$value"
            }

            // Check cache first
            val cachedEntry = cache[queryKey]
            if (cachedEntry != null) {
                // Update ref count
                cache[queryKey] = cachedEntry.copy(refCount = cachedEntry.refCount + 1)
                
                @Suppress("UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")
                result.value = DataViewResult(loading = false, data = cachedEntry.data as? T)
                return@addSource
            }

            // Check if request is already in flight
            val inFlightRequest = inFlightRequests[queryKey]
            if (inFlightRequest != null) {
                scope.launch {
                    try {
                        val data = inFlightRequest.await()
                        withContext(Dispatchers.Main) {
                        @Suppress("UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")
                        result.value = DataViewResult(loading = false, data = data as? T)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            result.value = DataViewResult(
                                loading = false, 
                                data = null, 
                                error = e.message
                            )
                        }
                    }
                }
                return@addSource
            }

            // Start new request
            result.value = DataViewResult(loading = true, data = null)
            
            val deferred = scope.async {
                fetchData(
                    state.loginAccount.url,
                    instanceId,
                    apiPath,
                    apiParams,
                    currentEventId,
                    state.refreshToken,
                    state.accessToken,
                    typeToken
                )
            }
            
            inFlightRequests[queryKey] = deferred
            
            scope.launch {
                try {
                    val data = deferred.await()
                    
                    // Cache the result
                    cache[queryKey] = DataViewCacheEntry(
                        data = data,
                        refCount = 1,
                        expiry = null
                    )
                    
                    // Clean up in-flight request
                    inFlightRequests.remove(queryKey)
                    
                    // Update result
                    withContext(Dispatchers.Main) {
                        result.value = DataViewResult(loading = false, data = data as? T)
                    }
                    
                    // Clean expired cache entries
                    cleanExpiredEntries()
                    
                } catch (e: Exception) {
                    inFlightRequests.remove(queryKey)
                    
                    withContext(Dispatchers.Main) {
                        result.value = DataViewResult(
                            loading = false, 
                            data = null, 
                            error = "Error fetching data: ${e.message}"
                        )
                    }
                }
            }
        }
        
        return result
    }

    private suspend fun <T> fetchData(
        baseUrl: String,
        instanceId: String,
        apiPath: String,
        apiParams: Map<String, String>,
        currentEventId: Int,
        refreshToken: String,
        accessToken: String,
        typeToken: TypeToken<T>
    ): T? {
        var encodedUrl = "$baseUrl/$instanceId/$apiPath?e=$currentEventId"
        apiParams.forEach { (key, value) ->
            encodedUrl += "&$key=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }

        val response = authService.fetchAuthenticated(
            encodedUrl,
            refreshToken,
            accessToken
        )

        return response.use {
            if (it.isSuccessful) {
                val responseBody = it.body?.string()
                if (responseBody != null) {
                    gson.fromJson<T>(responseBody, typeToken.type)
                } else {
                    null
                }
            } else {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }
        }
    }

    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<String>()
        
        cache.forEach { (key, entry) ->
            if (entry.refCount == 0 && 
                entry.expiry != null && 
                entry.expiry < now) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { key ->
            cache.remove(key)
        }
    }

    fun decrementRefCount(queryKey: String) {
        val entry = cache[queryKey] ?: return
        val newRefCount = maxOf(0, entry.refCount - 1)
        
        if (newRefCount == 0) {
            // Set expiry to 5 minutes from now
            cache[queryKey] = entry.copy(
                refCount = newRefCount,
                expiry = System.currentTimeMillis() + 300000 // 5 minutes
            )
        } else {
            cache[queryKey] = entry.copy(refCount = newRefCount)
        }
    }

    fun destroy() {
        scope.cancel()
        cache.clear()
        inFlightRequests.clear()
    }
}