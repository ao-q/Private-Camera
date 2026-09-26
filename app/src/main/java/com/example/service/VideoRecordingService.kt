package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.camera.model.CameraInfoModel
import com.example.data.local.MediaItemEntity
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VideoRecordingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    private lateinit var mediaRepository: MediaRepository

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private var activeCameraModel: CameraInfoModel? = null
    private var isAudioEnabled: Boolean = true
    private var currentTempFile: File? = null
    private var videoWidth: Int = 1920
    private var videoHeight: Int = 1080
    private var orientationDegrees: Int = 0

    // Callback when recording finishes so UI/Controller can react
    var onRecordingCompleted: ((MediaItemEntity?) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): VideoRecordingService = this@VideoRecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaRepository = MediaRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_RECORDING -> {
                stopActiveRecording()
            }
        }
        return START_NOT_STICKY
    }

    fun startForegroundRecording(
        cameraModel: CameraInfoModel,
        audioEnabled: Boolean,
        tempVideoFile: File,
        width: Int,
        height: Int,
        orientation: Int
    ) {
        activeCameraModel = cameraModel
        isAudioEnabled = audioEnabled
        currentTempFile = tempVideoFile
        videoWidth = width
        videoHeight = height
        orientationDegrees = orientation

        _isRecording.value = true
        _recordingDurationMs.value = 0L

        val notification = buildNotification(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (audioEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        val startTime = System.currentTimeMillis()
        timerJob = serviceScope.launch {
            while (isActive && _isRecording.value) {
                val elapsed = System.currentTimeMillis() - startTime
                _recordingDurationMs.value = elapsed
                updateNotification(elapsed)
                delay(1000)
            }
        }
    }

    fun stopActiveRecording(): MediaItemEntity? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        timerJob?.cancel()

        val tempFile = currentTempFile
        val duration = _recordingDurationMs.value
        val camera = activeCameraModel

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        var savedEntity: MediaItemEntity? = null
        if (tempFile != null && camera != null && duration > 500) {
            serviceScope.launch(Dispatchers.IO) {
                savedEntity = mediaRepository.finalizeVideoCapture(
                    tempVideoFile = tempFile,
                    cameraId = camera.cameraId,
                    cameraLabel = camera.displayName,
                    width = videoWidth,
                    height = videoHeight,
                    orientationDegrees = orientationDegrees,
                    durationMs = duration
                )
                onRecordingCompleted?.invoke(savedEntity)
            }
        }

        return savedEntity
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Recording Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing video recording status and quick stop action"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        val totalSecs = elapsedMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val timeString = String.format("%02d:%02d", mins, secs)

        val cameraName = activeCameraModel?.displayName ?: "Camera"
        val micStatus = if (isAudioEnabled) "Mic: On" else "Mic: Muted"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VideoRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video recording in progress")
            .setContentText("$timeString • $cameraName • $micStatus")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Recording",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(elapsedMs: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(elapsedMs))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "private_camera_recording_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"

        fun startServiceIntent(context: Context): Intent {
            return Intent(context, VideoRecordingService::class.java)
        }
    }
}
