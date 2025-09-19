package com.tomyedwab.yellowstone.utils

import android.content.Context
import java.io.IOException

class BinaryReader(private val context: Context) {
    
    /**
     * Reads the embedded Go binary from assets
     * @return ByteArray containing the binary data
     * @throws IOException if the binary cannot be read
     */
    fun readBinary(): ByteArray {
        return try {
            context.assets.open("yellowstone-binary").use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            throw IOException("Failed to read embedded binary: ${e.message}", e)
        }
    }
    
    /**
     * Reads the MD5 hash of the embedded Go binary
     * @return String containing the MD5 hash
     * @throws IOException if the MD5 file cannot be read
     */
    fun readBinaryMD5(): String {
        return try {
            context.assets.open("yellowstone-binary.md5").use { inputStream ->
                inputStream.bufferedReader().readText().trim()
            }
        } catch (e: Exception) {
            throw IOException("Failed to read binary MD5 hash: ${e.message}", e)
        }
    }
    
    /**
     * Gets the size of the embedded binary
     * @return Long representing the binary size in bytes
     */
    fun getBinarySize(): Long {
        return try {
            context.assets.openFd("yellowstone-binary").use { fd ->
                fd.length
            }
        } catch (e: Exception) {
            0L
        }
    }
}