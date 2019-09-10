package com.stevesoltys.backup.transport.backup

import android.app.backup.BackupTransport.TRANSPORT_ERROR
import android.app.backup.BackupTransport.TRANSPORT_OK
import com.stevesoltys.backup.BackupNotificationManager
import com.stevesoltys.backup.metadata.MetadataWriter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream
import kotlin.random.Random

internal class BackupCoordinatorTest: BackupTest() {

    private val plugin = mockk<BackupPlugin>()
    private val kv = mockk<KVBackup>()
    private val full = mockk<FullBackup>()
    private val metadataWriter = mockk<MetadataWriter>()
    private val notificationManager = mockk<BackupNotificationManager>()

    private val backup = BackupCoordinator(plugin, kv, full, metadataWriter, notificationManager)

    private val metadataOutputStream = mockk<OutputStream>()

    @Test
    fun `device initialization succeeds and delegates to plugin`() {
        every { plugin.initializeDevice() } just Runs
        expectWritingMetadata()
        every { kv.hasState() } returns false
        every { full.hasState() } returns false

        assertEquals(TRANSPORT_OK, backup.initializeDevice())
        assertEquals(TRANSPORT_OK, backup.finishBackup())
    }

    @Test
    fun `device initialization fails`() {
        every { plugin.initializeDevice() } throws IOException()
        every { notificationManager.onBackupError() } just Runs

        assertEquals(TRANSPORT_ERROR, backup.initializeDevice())

        // finish will only be called when TRANSPORT_OK is returned, so it should throw
        every { kv.hasState() } returns false
        every { full.hasState() } returns false
        assertThrows(IllegalStateException::class.java) {
            backup.finishBackup()
        }
    }

    @Test
    fun `getBackupQuota() delegates to right plugin`() {
        val isFullBackup = Random.nextBoolean()
        val quota = Random.nextLong()

        if (isFullBackup) {
            every { full.getQuota() } returns quota
        } else {
            every { kv.getQuota() } returns quota
        }
        assertEquals(quota, backup.getBackupQuota(packageInfo.packageName, isFullBackup))
    }

    @Test
    fun `clearing KV backup data throws`() {
        every { kv.clearBackupData(packageInfo) } throws IOException()

        assertEquals(TRANSPORT_ERROR, backup.clearBackupData(packageInfo))
    }

    @Test
    fun `clearing full backup data throws`() {
        every { kv.clearBackupData(packageInfo) } just Runs
        every { full.clearBackupData(packageInfo) } throws IOException()

        assertEquals(TRANSPORT_ERROR, backup.clearBackupData(packageInfo))
    }

    @Test
    fun `clearing backup data succeeds`() {
        every { kv.clearBackupData(packageInfo) } just Runs
        every { full.clearBackupData(packageInfo) } just Runs

        assertEquals(TRANSPORT_OK, backup.clearBackupData(packageInfo))

        every { kv.hasState() } returns false
        every { full.hasState() } returns false

        assertEquals(TRANSPORT_OK, backup.finishBackup())
    }

    @Test
    fun `finish backup delegates to KV plugin if it has state`() {
        val result = Random.nextInt()

        every { kv.hasState() } returns true
        every { full.hasState() } returns false
        every { kv.finishBackup() } returns result

        assertEquals(result, backup.finishBackup())
    }

    @Test
    fun `finish backup delegates to full plugin if it has state`() {
        val result = Random.nextInt()

        every { kv.hasState() } returns false
        every { full.hasState() } returns true
        every { full.finishBackup() } returns result

        assertEquals(result, backup.finishBackup())
    }

    private fun expectWritingMetadata() {
        every { plugin.getMetadataOutputStream() } returns metadataOutputStream
        every { metadataWriter.write(metadataOutputStream) } just Runs
    }

}