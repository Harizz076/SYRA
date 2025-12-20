package com.wboelens.polarrecorder.utils

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    /**
     * Zips all folders starting with "syra_" in the base directory
     * @param context Android context
     * @param baseDir The base directory containing syra_timestamp folders
     * @return File object of the created zip file, or null if failed
     */
    fun zipAllSyraFolders(context: Context, baseDir: DocumentFile): File? {
        try {
            // Create a temporary zip file in cache directory
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val zipFile = File(context.cacheDir, "syra_logs_$timestamp.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipOut ->
                // Get all folders that start with "syra_"
                val syraDirs = baseDir.listFiles().filter {
                    it.isDirectory && it.name?.startsWith("syra_") == true
                }

                if (syraDirs.isEmpty()) {
                    return null
                }

                // Add each syra folder to the zip
                syraDirs.forEach { syraDir ->
                    addFolderToZip(context, syraDir, syraDir.name ?: "unknown", zipOut)
                }
            }

            return zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Recursively adds a folder and its contents to a zip output stream
     */
    private fun addFolderToZip(
        context: Context,
        folder: DocumentFile,
        parentPath: String,
        zipOut: ZipOutputStream
    ) {
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                // Recursively add subdirectory
                val dirPath = "$parentPath/${file.name}"
                addFolderToZip(context, file, dirPath, zipOut)
            } else if (file.isFile) {
                // Add file to zip
                val filePath = "$parentPath/${file.name}"
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        BufferedInputStream(inputStream).use { bufferedInput ->
                            val entry = ZipEntry(filePath)
                            zipOut.putNextEntry(entry)

                            val buffer = ByteArray(8192)
                            var length: Int
                            while (bufferedInput.read(buffer).also { length = it } > 0) {
                                zipOut.write(buffer, 0, length)
                            }

                            zipOut.closeEntry()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Creates a share intent for a file
     */
    fun createShareIntent(context: Context, file: File): android.content.Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

