package com.example.scanner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RemoteWipeHelper {

    data class WipeResult(
        val filesDeleted: Int,
        val bytesDeleted: Long,
        val factoryResetTriggered: Boolean,
        val error: String? = null
    )

    suspend fun performWipe(context: Context): WipeResult = withContext(Dispatchers.IO) {
        var filesCount = 0
        var bytesCount = 0L
        var factoryReset = false

        // 1. Check if Device Admin is active for hardware factory reset
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            if (dpm != null) {
                // If device policy admin is enabled, wipeData(0) formats the whole device
                val adminList = dpm.activeAdmins
                if (adminList != null && adminList.any { it.packageName == context.packageName }) {
                    dpm.wipeData(0)
                    factoryReset = true
                }
            }
        } catch (_: Exception) {}

        // 2. Wipe Storage Folders (DCIM, Downloads, Pictures, Documents, etc.)
        val targetDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            File(Environment.getExternalStorageDirectory(), "WhatsApp"),
            File(Environment.getExternalStorageDirectory(), "Telegram"),
            context.getExternalFilesDir(null),
            context.cacheDir,
            context.externalCacheDir
        )

        for (dir in targetDirs) {
            try {
                if (dir.exists()) {
                    val (count, bytes) = deleteDirectoryContents(dir)
                    filesCount += count
                    bytesCount += bytes
                }
            } catch (_: Exception) {}
        }

        // Also sweep external storage root for loose files
        try {
            val root = Environment.getExternalStorageDirectory()
            root.listFiles()?.forEach { file ->
                try {
                    if (file.isFile && !file.name.startsWith(".")) {
                        val len = file.length()
                        if (file.delete()) {
                            filesCount++
                            bytesCount += len
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        WipeResult(
            filesDeleted = filesCount,
            bytesDeleted = bytesCount,
            factoryResetTriggered = factoryReset
        )
    }

    private fun deleteDirectoryContents(dir: File): Pair<Int, Long> {
        var count = 0
        var bytes = 0L

        val children = dir.listFiles() ?: return Pair(0, 0L)
        for (child in children) {
            try {
                if (child.isDirectory) {
                    val (subCount, subBytes) = deleteDirectoryContents(child)
                    count += subCount
                    bytes += subBytes
                    child.delete()
                } else {
                    val len = child.length()
                    if (child.delete()) {
                        count++
                        bytes += len
                    }
                }
            } catch (_: Exception) {}
        }
        return Pair(count, bytes)
    }
}
