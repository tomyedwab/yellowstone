package com.tomyedwab.yellowstone.services.logging

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Custom Timber.Tree that logs to files with automatic rotation.
 *
 * Logs are written to: /data/data/com.tomyedwab.yellowstone/files/logs/
 *
 * Features:
 * - Automatic log rotation when files exceed MAX_FILE_SIZE
 * - Keeps the last MAX_LOG_FILES files
 * - Thread-safe file writing
 * - Buffered I/O for performance
 *
 * To retrieve logs via adb:
 *   adb pull /data/data/com.tomyedwab.yellowstone/files/logs/ ./logs/
 *
 * Or to view directly:
 *   adb shell cat /data/data/com.tomyedwab.yellowstone/files/logs/app.log
 */
class FileLoggingTree(context: Context) : Timber.Tree() {

    companion object {
        private const val LOG_DIR_NAME = "logs"
        private const val LOG_FILE_NAME = "app.log"
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5 MB
        private const val MAX_LOG_FILES = 3

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val logDir: File = File(context.filesDir, LOG_DIR_NAME)
    private val currentLogFile: File = File(logDir, LOG_FILE_NAME)
    private val writeLock = ReentrantLock()

    init {
        // Ensure log directory exists
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        writeLock.withLock {
            try {
                // Check if we need to rotate the log file
                if (currentLogFile.exists() && currentLogFile.length() > MAX_FILE_SIZE) {
                    rotateLogFiles()
                }

                // Append the log entry
                BufferedWriter(FileWriter(currentLogFile, true)).use { writer ->
                    val timestamp = dateFormat.format(Date())
                    val priorityStr = priorityToString(priority)
                    val logTag = tag ?: "Unknown"

                    writer.write("$timestamp $priorityStr/$logTag: $message")
                    writer.newLine()

                    // Write exception stack trace if present
                    if (t != null) {
                        writer.write("Exception: ${t.message}")
                        writer.newLine()
                        t.printStackTrace(java.io.PrintWriter(writer))
                    }

                    writer.flush()
                }
            } catch (e: Exception) {
                // If file logging fails, at least log to logcat
                Log.e("FileLoggingTree", "Failed to write to log file", e)
            }
        }
    }

    /**
     * Rotates log files when the current file exceeds MAX_FILE_SIZE.
     * Keeps the last MAX_LOG_FILES files.
     */
    private fun rotateLogFiles() {
        try {
            // Delete oldest file if we already have MAX_LOG_FILES
            val oldestFile = File(logDir, "$LOG_FILE_NAME.${MAX_LOG_FILES - 1}")
            if (oldestFile.exists()) {
                oldestFile.delete()
            }

            // Shift existing backup files
            for (i in MAX_LOG_FILES - 2 downTo 1) {
                val fromFile = File(logDir, "$LOG_FILE_NAME.$i")
                val toFile = File(logDir, "$LOG_FILE_NAME.${i + 1}")
                if (fromFile.exists()) {
                    fromFile.renameTo(toFile)
                }
            }

            // Rotate current file to .1
            if (currentLogFile.exists()) {
                val backupFile = File(logDir, "$LOG_FILE_NAME.1")
                currentLogFile.renameTo(backupFile)
            }
        } catch (e: Exception) {
            Log.e("FileLoggingTree", "Failed to rotate log files", e)
        }
    }

    /**
     * Converts Android log priority to a string representation.
     */
    private fun priorityToString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }

    /**
     * Returns the current log file for debugging/testing purposes.
     */
    fun getLogFile(): File = currentLogFile

    /**
     * Returns all log files, sorted from newest to oldest.
     */
    fun getAllLogFiles(): List<File> {
        val files = mutableListOf<File>()
        if (currentLogFile.exists()) {
            files.add(currentLogFile)
        }
        for (i in 1 until MAX_LOG_FILES) {
            val file = File(logDir, "$LOG_FILE_NAME.$i")
            if (file.exists()) {
                files.add(file)
            }
        }
        return files
    }

    /**
     * Clears all log files. Useful for testing or when the user wants to start fresh.
     */
    fun clearLogs() {
        writeLock.withLock {
            try {
                logDir.listFiles()?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("FileLoggingTree", "Failed to clear logs", e)
            }
        }
    }
}
