package com.example.overdex.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** A user-owned screenshot library selected once through Android's folder picker. */
class ScreenshotDirectoryManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("overdex_screenshot_directory", Context.MODE_PRIVATE)

    fun folderUri(): Uri? = preferences.getString("folder_uri", null)?.let(Uri::parse)

    fun saveFolder(uri: Uri) {
        preferences.edit().putString("folder_uri", uri.toString()).apply()
    }

    fun displayName(): String = folderUri()?.let { uri ->
        DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment
    } ?: "Bundled samples"

    fun imageUris(): List<Uri> {
        val folder = folderUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return emptyList()
        return folder.listFiles()
            .filter { file -> file.isFile && file.type?.startsWith("image/") == true }
            .sortedBy { it.name?.lowercase() }
            .map { it.uri }
    }
}
