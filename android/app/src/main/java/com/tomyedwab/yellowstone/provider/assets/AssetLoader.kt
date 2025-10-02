import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class ComponentAsset(val binaryAssetName: String, val md5AssetName: String)

interface AssetLoaderInterface {
    fun loadComponentHashes(): ConcurrentHashMap<String, String>
    fun loadComponentBinary(componentName: String): ByteArray?
}

class AssetLoader(private val context: Context) : AssetLoaderInterface {
    private lateinit var componentAssetMap: Map<String, ComponentAsset>

    private fun loadAssetMap() {
        if (!::componentAssetMap.isInitialized) {
            componentAssetMap = context.assets.list("components")?.mapNotNull { assetName ->
                if (assetName.endsWith(".zip")) {
                    ComponentAsset("components/" + assetName, "components/" + assetName.replace(".zip", ".md5"))
                } else {
                    null
                }
            }?.associateBy { it.binaryAssetName.replace("components/", "").replace(".zip", "") } ?: emptyMap()
        }
    }

    override fun loadComponentHashes(): ConcurrentHashMap<String, String> {
        loadAssetMap()

        val componentHashes = ConcurrentHashMap<String, String>()
        componentAssetMap.forEach { (componentName, assetInfo) ->
            try {
                // Read MD5 hash from assets
                val md5Hash =
                        context.assets.open(assetInfo.md5AssetName).use { inputStream ->
                            inputStream.bufferedReader().readText().trim()
                        }
                componentHashes[componentName] = md5Hash
                Log.i("ConnectionService", "Loaded hash for $componentName: $md5Hash")
            } catch (e: Exception) {
                Log.e("ConnectionService", "Failed to load hash for $componentName", e)
            }
        }
        return componentHashes
    }

    override fun loadComponentBinary(componentName: String): ByteArray? {
        loadAssetMap()

        return try {
            val assetInfo = componentAssetMap[componentName]
            if (assetInfo != null) {
                val binaryData =
                        context.assets.open(assetInfo.binaryAssetName).use { inputStream ->
                            inputStream.readBytes()
                        }
                Log.i(
                        "ConnectionService",
                        "Loaded binary for $componentName: ${binaryData.size} bytes"
                )
                binaryData
            } else {
                Log.w(
                        "ConnectionService",
                        "No asset mapping found for component: $componentName"
                )
                null
            }
        } catch (e: Exception) {
            Log.e("ConnectionService", "Failed to load binary for $componentName", e)
            null
        }
    }
}
