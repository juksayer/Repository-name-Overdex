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

    data class Image(val uri: Uri, val name: String, val deviceLabel: String? = null)

    /** Root images plus one optional subfolder per phone/model. Called off the UI thread. */
    fun images(): List<Image> {
        val folder = folderUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return emptyList()
        return folder.listFiles().flatMap { entry ->
            when {
                entry.isDirectory -> entry.listFiles().mapNotNull { image -> image.asImage(entry.name) }
                else -> listOfNotNull(entry.asImage(null))
            }
        }.sortedWith(compareBy({ it.deviceLabel?.lowercase().orEmpty() }, { it.name.lowercase() }))
    }

    fun imageUris(): List<Uri> = images().map { it.uri }

    private fun DocumentFile.asImage(deviceLabel: String?): Image? =
        takeIf { isFile && type?.startsWith("image/") == true }
            ?.let { Image(it.uri, it.name ?: "Screenshot", deviceLabel?.takeIf(String::isNotBlank)) }
}
