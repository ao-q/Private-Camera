package com.example

import com.example.data.repository.StorageMonitor
import org.junit.Assert.*
import org.junit.Test

class StorageMonitorTest {

    @Test
    fun testFormatBytes_zeroAndPositive() {
        assertEquals("0 B", StorageMonitor.formatBytes(0))
        assertEquals("500.0 B", StorageMonitor.formatBytes(500))
        assertEquals("1.0 KB", StorageMonitor.formatBytes(1024))
        assertEquals("1.5 MB", StorageMonitor.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("3.0 GB", StorageMonitor.formatBytes((3L * 1024 * 1024 * 1024)))
    }
}
