# Private Camera

**Private Camera** is a native, privacy-focused Android camera application built using **Kotlin**, **Jetpack Compose**, **Material 3**, and **Camera2**.

The app operates 100% offline with zero cloud services, zero analytics, and no accounts. All captured photos and videos are stored in an app-private sandbox and never leak to the Android system gallery or cloud backup without explicit user consent.

---

## Key Features

1. **True Lossless PNG Photography**:
   - Captures uncompressed YUV camera streams directly and encodes them to full-resolution PNG.
   - Never converts from intermediate JPEG files, eliminating compression artifacts and noise reduction blurring.
2. **Automatic RAW (DNG) Sensor Backup**:
   - On RAW-capable cameras, automatically saves a sensor-original master `.dng` file alongside the lossless `.png` copy for the same shutter press.
   - Dual-format captures are grouped as a single entry in the gallery with a `PNG + RAW` badge.
3. **Continuous Background Video Recording**:
   - Video recordings are owned by a foreground service with `camera` and `microphone` types.
   - Recording continues uninterrupted when the user presses Home, turns off the screen, or locks the phone.
   - Ongoing lock-screen notification displays elapsed time, selected camera, audio status, and a prominent "Stop Recording" action.
4. **Complete Camera2 Lens Discovery**:
   - Proactively enumerates all camera IDs and physical camera sensors exposed by Android's public Camera2 APIs.
   - Calculates 35mm equivalent focal lengths and relative zoom factors (`0.6× Ultrawide`, `1× Main`, `3× Telephoto`, `Front 1×`).
   - Technical inspection modal for physical sensor size, hardware level (Legacy, Limited, Full, Level 3), OIS, and FPS ranges.
5. **Private In-App Gallery & Room DB**:
   - All captures are saved strictly in `context.filesDir` (`photos/`, `videos/`, `thumbnails/`, `raw/`).
   - Room database manages metadata, orientation, focal lengths, aperture, ISO, and file sizes.
   - Multi-select mode with batch share, SAF export to device storage, and atomic deletion.

---

## Architecture Overview

```
app/
├── camera/
│   ├── capture/
│   │   ├── LosslessPngEncoder.kt   # Direct YUV_420_888 to PNG encoder
│   │   └── RawDngWriter.kt         # DngCreator sensor master writer
│   ├── discovery/
│   │   └── CameraDiscoveryManager.kt # Multi-lens & physical sensor discovery
│   ├── model/
│   │   └── CameraInfoModel.kt      # Hardware specs & lens classification
│   └── session/
│       └── CameraSessionController.kt # Camera2 session lifecycle & detach/attach
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # Room database
│   │   ├── MediaDao.kt             # Media CRUD operations
│   │   └── MediaItemEntity.kt      # Capture metadata entity
│   └── repository/
│       ├── MediaRepository.kt      # Private storage lifecycle & SAF export
│       ├── SettingsRepository.kt   # Preferences (RAW toggle, video res)
│       └── StorageMonitor.kt       # StatFs remaining photos/video capacity
├── service/
│   └── VideoRecordingService.kt    # Foreground service for lock-screen recording
└── ui/
    ├── camera/                     # Camera preview, top/bottom bars, sheets
    ├── gallery/                    # Private media grid & batch selection
    ├── viewer/                     # Fullscreen photo zoom & video playback
    ├── settings/                   # App preferences & storage breakdown
    └── theme/                      # Material 3 cinematic dark palette
```

---

## Physical-Device Testing Checklist

1. **Lossless PNG Capture**:
   - Take a photo in Photo mode. Verify the saved file is an uncompressed PNG without JPEG compression artifacts.
2. **Automatic RAW (DNG) Capture**:
   - On a RAW-capable camera with "Also save RAW" enabled, take a photo.
   - Check the gallery entry displays the `PNG + RAW` badge and the technical sheet shows both PNG and DNG files.
3. **Multi-Camera & Physical Sensor Selection**:
   - Tap the lens pills (`0.6x`, `1x`, `3x`, `Front`) and verify the camera preview switches seamlessly without crashing.
   - Open the "Discovered Cameras" sheet to verify logical vs physical sensor breakdown.
4. **Continuous Video Recording Across Device Lock**:
   - Switch to Video mode and tap Record.
   - Press the Power button to turn off the screen / lock the device.
   - Keep the device locked for 1-2 minutes.
   - Unlock the device or tap "Stop Recording" directly on the lock screen notification.
   - Verify the MP4 video is playable and finalized safely.
5. **Microphone Audio Toggle**:
   - Record video with Mic ON, verify audio track is present.
   - Toggle Mic OFF, record silent video; verify no audio permission is requested when muted.
6. **In-App Private Gallery Isolation**:
   - Check that captures appear only in Private Camera's in-app gallery and do NOT appear in Google Photos or the system gallery.
7. **Export & Sharing**:
   - Tap Share on a `PNG + RAW` photo and test sharing PNG only, RAW (.dng) only, or both.
   - Tap Export to save a copy to the device's Downloads/Documents folder via SAF.
8. **Deletion & Filesystem Sync**:
   - Delete an item from the gallery or media viewer. Confirm the PNG, DNG, and thumbnail files are deleted from private storage.
