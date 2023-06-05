package com.tools.phone_captions.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val contentResolver = applicationContext.contentResolver

        // Get the folderUri and zipUri from inputData
        val zipUriString = inputData.getString("zip_uri")
        val folderUriString = inputData.getString("folder_uri")

        val zipUri = Uri.parse(zipUriString)
        val folderUri = Uri.parse(folderUriString)

        // Check if the URIs are valid
        if (zipUri == null || folderUri == null) {
            return Result.failure()
        }

        return try {
            runBlocking {
                zipFolder(contentResolver, zipUri, folderUri)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun zipFolder(contentResolver: ContentResolver, zipUri: Uri, folderUri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(applicationContext, folderUri)

        val filesToZip = documentFile?.listFiles()?.filter {
            it.isFile && (it.type?.startsWith("image/") == true || it.type == "text/plain")
        }

        val zipOutputStream = contentResolver.openOutputStream(zipUri)?.let { ZipOutputStream(BufferedOutputStream(it)) }

        filesToZip?.forEach { file ->
            zipOutputStream?.putNextEntry(ZipEntry(file.name))
            val inputStream = contentResolver.openInputStream(file.uri)
            inputStream?.copyTo(zipOutputStream!!)
            zipOutputStream?.closeEntry()
            inputStream?.close()
        }

        zipOutputStream?.close()
    }
}
