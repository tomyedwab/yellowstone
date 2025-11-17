package com.tomyedwab.yellowstone

import android.app.Application
import com.tomyedwab.yellowstone.services.logging.FileLoggingTree
import timber.log.Timber

/**
 * Application class for Yellowstone.
 *
 * Initializes Timber logging with both console and file logging.
 */
class YellowstoneApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber logging
        initializeLogging()
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            // In debug builds, log to both console (logcat) and file
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLoggingTree(this))
            Timber.d("Timber initialized with console and file logging")
        } else {
            // In release builds, only log to file
            Timber.plant(FileLoggingTree(this))
            Timber.i("Timber initialized with file logging only")
        }
    }
}
