package com.example

import android.app.Application
import com.example.data.repository.MediaRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.StorageMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivateCameraApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var storageMonitor: StorageMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        mediaRepository = MediaRepository(this)
        settingsRepository = SettingsRepository(this)
        storageMonitor = StorageMonitor(this)

        // Clean stale temp files and ensure database integrity on launch
        applicationScope.launch {
            mediaRepository.reconcileDatabaseWithFilesystem()
        }
    }
}
