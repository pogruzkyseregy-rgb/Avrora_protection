package com.avrora.protection

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Один APK-файл из выбранной пользователем папки. */
data class ApkItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val modified: Long
)

object ApkFolderScanner {

    /**
     * Возвращает APK в папке, которую пользователь сам выбрал через системное окно.
     * Android не даёт читать папку «Загрузки» без такого выбора.
     */
    fun list(context: Context, folder: Uri): List<ApkItem> {
        val dir = DocumentFile.fromTreeUri(context, folder) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".apk", ignoreCase = true) == true }
            .map { ApkItem(it.uri, it.name.orEmpty(), it.length(), it.lastModified()) }
            .sortedByDescending { it.modified }
    }
}
