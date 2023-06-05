package com.tools.phone_captions.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.tools.phone_captions.PhoneCaptionsApplication
import com.tools.phone_captions.entities.ThumbnailEntity

import com.tools.phone_captions.models.Picture
import com.tools.phone_captions.models.Thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

class PicturesRepository(private val context: Context, private val folderUri: Uri) {

    suspend fun refreshFolderDatabase(folderUri: Uri) = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val thumbnailEntities = mutableListOf<ThumbnailEntity>()
        val txtFileExist = getTxtFileExistMap(folderUri)

        PhoneCaptionsApplication.database.thumbnailDao().deleteByFolder(folderUri.toString())

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))

        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                val id = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                val isDirectory = DocumentFile.fromSingleUri(context, fileUri)?.isDirectory ?: false

                if (!isDirectory) {
                    if (mimeType.startsWith("text")) {
                        // Do nothing, we build text file URIs later
                    } else if (isImageMimeType(mimeType)) {
                        val txtFileName = name.substringBeforeLast(".") + ".txt"
                        val txtFileUri = generateTxtUriFromImageUri(fileUri)
                        var description = ""
                        txtFileExist[txtFileName]?.let { exists ->
                            if (exists) {
                                description = txtFileUri?.let { readDescription(it) } ?: ""
                            }
                        }
                        val thumbnailEntity = ThumbnailEntity.create(
                            imageUri = fileUri.toString(),
                            txtUri = txtFileUri?.toString(),
                            description = description,
                            folderUri = folderUri?.toString()
                        )
                        thumbnailEntities.add(thumbnailEntity)

                        // Insert into Room database
                    }
                }
            }
        }

        // Step 3: Insert new thumbnail entities
        PhoneCaptionsApplication.database.thumbnailDao().insertAll(thumbnailEntities)
    }


    suspend fun getThumbnails(folderUri: Uri): List<Thumbnail> = withContext(Dispatchers.Main) {
        val thumbnailsList = mutableListOf<Thumbnail>()
        // Get ThumbnailDao instance from your AppDatabase
        //mval thumbnailDao = AppDatabase.getInstance(context).thumbnailDao()
        val thumbnailDao =  PhoneCaptionsApplication.database.thumbnailDao()
        // Fetch all thumbnails from database
        val thumbnailEntitiesFromDB = thumbnailDao.getFolderThumbnails(folderUri.toString())

        thumbnailEntitiesFromDB.forEach { entity ->
            val imageUri = Uri.parse(entity.imageUri)
            val thumbnailUri = getThumbnailUri(imageUri)

            if (entity.txtUri != null) {
                val txtUri = entity.txtUri.let { Uri.parse(entity.txtUri) }
                val description = entity.description
                val thumbnail = Thumbnail(thumbnailUri, description ?: "", imageUri)
                thumbnailsList.add(thumbnail)
            }
        }
        return@withContext thumbnailsList
    }
/*
    suspend fun getThumbnails_b(folderUri: Uri): List<Thumbnail> = withContext(Dispatchers.IO) {
        val thumbnailsList = mutableListOf<Thumbnail>()
        val contentResolver = context.contentResolver
        // Get the map of text files
        val txtFilesMap = getTxtFileExistMap(folderUri)

        // Create a URI that points to the directory's children (files)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))

        // Query the content resolver for all images in the folder
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                val id = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                // Check if the fileUri points to a directory
                val isDirectory = DocumentFile.fromSingleUri(context, fileUri)?.isDirectory ?: false

                if (!isDirectory) {
                    // Check the MIME type
                    if (mimeType.startsWith("text")) {
                        // Do nothing, we build text file URIs later
                    } else if (isImageMimeType(mimeType)) {
                        val thumbnailUri = getThumbnailUri(fileUri)
                        if (thumbnailUri == null) {
                            Log.d("Warning:", "Missing thumbnail ${fileUri.toString()}")
                            continue
                        }

                        val thumbnail = Thumbnail(thumbnailUri, "", fileUri)

                        // Build text file name from image file name
                        val txtFileName = name.substringBeforeLast(".") + ".txt"

                        // If a .txt file with the corresponding name exists, read the description from it
                        val txtFileUri = txtFilesMap[txtFileName]
                        if (txtFileUri != null) {
                            val description = readDescription(txtFileUri)
                            thumbnail.description = description
                        }


                        thumbnailsList.add(thumbnail)
                    }
                }
            }
        }
        return@withContext thumbnailsList
    }*/

    suspend fun getTxtFileExistMap(folderUri: Uri): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val txtFileExist = mutableMapOf<String, Boolean>()
        val contentResolver = context.contentResolver

        // Create a URI that points to the directory's children (files)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))

        // Query the content resolver for all text files in the folder
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            // Iterate through the results, and add text files to the map
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                val id = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                val isDirectory = DocumentFile.fromSingleUri(context, fileUri)?.isDirectory ?: false

                if (!isDirectory && mimeType.startsWith("text")) {
                    // Store text files in map
                    txtFileExist[name] = true
                }
            }
        }
        return@withContext txtFileExist
    }

    suspend fun getFolderThumbnails(folderUri: Uri): List<ThumbnailEntity> {
        return PhoneCaptionsApplication.database.thumbnailDao().getFolderThumbnails(folderUri.toString())
    }


    fun getFolderUri(): Uri? {
        return folderUri
    }

    fun getFileSize(context: Context, fileUri: Uri): Long? {
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                val byteArray = inputStream.readBytes()
                byteArray.size.toLong()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listFilesInUri(directoryUri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getDocumentId(directoryUri)
        )

        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )

        cursor?.use {
            val displayNameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(displayNameIndex)
            }
        }
    }

    fun getPictures(fileUris: List<Uri>): List<Picture> {
        val picturesList = mutableListOf<Picture>()
        for (fileUri in fileUris) {
            val picture = Picture(fileUri, "")

            val txtFileUriString = generateTxtUriFromImageUri(fileUri).toString()
            val txtFileUri = Uri.parse(txtFileUriString)

            val documentFile = DocumentFile.fromSingleUri(context, txtFileUri)
            var mimeType: String? = null
            if (documentFile != null && documentFile.exists()) {
                // File exists, now you can get the MIME type
                mimeType = documentFile.type
            }

            // If the .txt file exists and its mime type is text, read the tags from it
            if (mimeType != null && isTextMimeType(mimeType)) {
                val description = readDescription(txtFileUri)
                picture.description = description
            }
            picturesList.add(picture)
        }
        return picturesList
    }

    private fun isTextMimeType(mimeType: String?): Boolean {
        val mime = mimeType ?: return false
        return mime.startsWith("text/")
    }

    private fun isImageMimeType(mimeType: String?): Boolean {
        val mime = mimeType ?: return false
        return mime.startsWith("image/")
    }

    fun updatePictureDescription(picture: Picture) {
        val tagFileName = picture.fileUri.lastPathSegment?.substringBeforeLast('.') + ".txt"
        val tagFileUri = Uri.withAppendedPath(picture.fileUri, tagFileName)
        val outputStream = context.contentResolver.openOutputStream(tagFileUri)
        outputStream?.bufferedWriter()?.use { it.write(picture.description) }
    }

    private fun readDescription(txtFileUri: Uri): String {
        return try {
            context.contentResolver.openInputStream(txtFileUri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                content
            } ?: ""
        } catch (e: FileNotFoundException) {
            Log.e("Read Description", "File not found: $txtFileUri", e)
            ""
        }
    }

    fun copyFileToPictureDirectory(uri: Uri, pictureDirectory: DocumentFile): String? {
        val displayName = getDisplayNameFromUri(uri)
        val extension = getFileExtension(displayName)
        val newFileName = renameExtensionToJpg(displayName, extension)

        val mimeType = "image/*" // Replace with the appropriate MIME type for your files

        val newFile = pictureDirectory?.createFile(mimeType, newFileName)
        val newFilePath = newFile?.uri?.path

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                newFile?.uri?.let { outputFileUri ->
                    context.contentResolver.openOutputStream(outputFileUri)?.use { outputStream ->
                        val buffer = ByteArray(4 * 1024) // 4KB buffer
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }
            }
            return newFilePath
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    fun generateTxtUriFromImageUri(imageUri: Uri): Uri {
        return Uri.parse(imageUri.toString().substringBeforeLast(".") + ".txt")
    }

    private fun getDisplayNameFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    val displayName = it.getString(displayNameIndex)
                    return displayName
                } else {
                    // Handle case when DISPLAY_NAME column is not found
                    // You can log an error message or return a default value
                }
            }
        }
        return null
    }

    private fun getFileExtension(fileName: String?): String {
        val dotIndex = fileName?.lastIndexOf('.')
        return if (dotIndex != null && dotIndex != -1 && dotIndex < fileName.length - 1) {
            fileName.substring(dotIndex + 1)
        } else {
            ""
        }
    }

    private fun renameExtensionToJpg(fileName: String?, extension: String): String {
        return if (extension.equals("jpeg", ignoreCase = true)) {
            fileName?.replaceAfterLast('.', "jpg") ?: ""
        } else {
            fileName ?: ""
        }
    }

    fun saveDescription(picture: Picture) {
        val descriptionText = picture.description
        // Update the description in the database
        val thumbnailDao = PhoneCaptionsApplication.database.thumbnailDao()
        GlobalScope.launch(Dispatchers.IO) {
            thumbnailDao.updateDescription(picture.fileUri.toString(), descriptionText)
            (context.applicationContext as PhoneCaptionsApplication).descriptionUpdatedFlow.emit(Unit)
        }

        val displayName = getDisplayNameFromUri(picture.fileUri)
        val txtFileName = displayName?.substringBeforeLast(".") + ".txt"

        val parentDocumentFile = DocumentFile.fromTreeUri(context, folderUri)
        var documentFile: DocumentFile? = null
        if (parentDocumentFile != null) {
            documentFile = parentDocumentFile.findFile(txtFileName)
            if (documentFile == null) {
                // create the file if it does not exist
                documentFile = parentDocumentFile.createFile("text/plain", txtFileName)
            }
        }

        if (documentFile != null) {
            context.contentResolver.openOutputStream(documentFile.uri)?.bufferedWriter()?.use {
                it.write(descriptionText)
            }
        }
    }

    fun writeInputStreamToUri(inputStream: InputStream, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    suspend fun ensureAllThumbnailsExist(folderUri: Uri) {
        val originalImageUris = getImageUrisInFolder(folderUri)
        originalImageUris.forEach { imageUri ->
            val thumbnailUri = getThumbnailUri(imageUri)
            val thumbnailFile = File(thumbnailUri.path)

            if (!thumbnailFile.exists()) {
                generateThumbnail(imageUri)
            }
        }
    }

    fun getImageUrisInFolder(folderUri: Uri): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val contentResolver = context.contentResolver

        // Create a URI that points to the directory's children (files)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))

        // Query the content resolver for all files in the folder
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )

        cursor?.use {
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)
                if (isImageMimeType(mimeType)) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                    imageUris.add(fileUri)
                }
            }
        }

        return imageUris
    }

    fun getThumbnailUri(originalImageUri: Uri): Uri {
        val originalImageFileName = getFileName(originalImageUri)
        val thumbnailFileName = "${originalImageFileName}_thumbnail.jpg"
        val thumbnailsDirUri = getThumbnailsDirectoryUri()
        return buildFileUri(thumbnailsDirUri, thumbnailFileName)
    }

    suspend fun generateThumbnail(originalImageUri: Uri, forceRegenerate: Boolean = false): Uri {
        val thumbnailUri = getThumbnailUri(originalImageUri)

        // Check if thumbnail file already exists
        if (forceRegenerate || !fileExists(thumbnailUri)) {
            createThumbnail(originalImageUri, thumbnailUri)
        }
        return thumbnailUri
    }

    fun getFileName(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast("/") ?: ""
    }

    fun getThumbnailsDirectoryUri(): Uri {
        val thumbnailsDir = File(context.getExternalFilesDir(null), "thumbnails")
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
        return Uri.fromFile(thumbnailsDir)
    }

    fun buildFileUri(directoryUri: Uri, fileName: String): Uri {
        return Uri.withAppendedPath(directoryUri, fileName)
    }

    private fun getFilePathFromContentUri(contentUri: Uri, contentResolver: ContentResolver): String {
        val filePath = ""
        val cursor = contentResolver.query(contentUri, null, null, null, null)
        if (cursor != null) {
            cursor.moveToFirst()
            val columnIndex = cursor.getColumnIndex("_data")
            return cursor.getString(columnIndex)
        }
        cursor?.close()
        return filePath
    }

    suspend fun createThumbnail(originalImageUri: Uri, thumbnailUri: Uri) {
        withContext(Dispatchers.IO) {
            val originalBitmap = BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(originalImageUri)
            )
            val thumbnailBitmap = ThumbnailUtils.extractThumbnail(
                originalBitmap,
                THUMBNAIL_WIDTH,
                THUMBNAIL_HEIGHT
            )
            val outStream = context.contentResolver.openOutputStream(thumbnailUri)
            thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outStream) // Reduced quality to 50
            outStream?.close()
        }
    }

    fun fileExists(fileUri: Uri): Boolean {
        val documentFile = DocumentFile.fromSingleUri(context, fileUri)
        return documentFile?.exists() ?: false
    }

    companion object {
        private const val THUMBNAIL_WIDTH = 100
        private const val THUMBNAIL_HEIGHT = 100
    }
}
