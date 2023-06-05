package com.tools.phone_captions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.github.chrisbanes.photoview.PhotoView
import com.tools.phone_captions.models.Picture
import com.tools.phone_captions.viewmodels.DescriptionViewModel
import com.tools.phone_captions.viewmodels.ViewModelFactory
import com.tools.phone_captions.data.PicturesRepository
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DescriptionActivity : AppCompatActivity() {
    private lateinit var descriptionViewModel: DescriptionViewModel
    private lateinit var photoView: PhotoView
    private var currentPictureIndex = 0
    private lateinit var picturesRepository: PicturesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_description)

        // Getting the DataMenuViewModel
        picturesRepository = (application as? PhoneCaptionsApplication)?.picturesRepository
            ?: throw IllegalStateException("Missing PicturesRepository")
        val factory = ViewModelFactory(picturesRepository!!)
        descriptionViewModel = ViewModelProvider(this, factory).get(DescriptionViewModel::class.java)

        photoView = findViewById(R.id.image_view)

        val fileUris: ArrayList<Uri>? = intent.getParcelableArrayListExtra("selectedPicturesUri")

        fileUris?.let {descriptionViewModel.loadPictures(it) }
        descriptionViewModel.setCurrentPosition(currentPictureIndex)


        // Now you can observe the currentPicture LiveData from DescriptionViewModel


        // Setup the toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val descriptionEditText = findViewById<EditText>(R.id.description_edit_text)
        val nextButton = findViewById<ImageButton>(R.id.next_button)
        val previousButton = findViewById<ImageButton>(R.id.previous_button)

        // handle left and right arrow click events here...
        nextButton.setOnClickListener {
            val description = descriptionEditText.text.toString()
            descriptionViewModel.saveDescription(description)
            descriptionViewModel.moveNext()
        }

        previousButton.setOnClickListener {
            val description = descriptionEditText.text.toString()
            descriptionViewModel.saveDescription(description)
            descriptionViewModel.movePrevious()
        }

        val cropButton = findViewById<ImageButton>(R.id.crop_button)

        cropButton.setOnClickListener {
            val picture = descriptionViewModel.currentPicture.value
            picture?.let {
                startCropping(it.fileUri)
            }
        }
        descriptionViewModel.currentPicture.observe(this, Observer { picture ->
            val nextPicture = descriptionViewModel.getNextPicture()
            val prevPicture = descriptionViewModel.getPreviousPicture()
            displayPicture(picture, nextPicture, prevPicture)

            displayPicture(picture, nextPicture, prevPicture)

            descriptionEditText.setText(picture.description)
        })

    }

    override fun onBackPressed() {
        val descriptionEditText = findViewById<EditText>(R.id.description_edit_text)
        val description = descriptionEditText.text.toString()

        // we use it so that when going to to DataMenu Activity, the database is updated
        (applicationContext as PhoneCaptionsApplication).isBackButtonPressed = true

        descriptionViewModel.saveDescription(description)

        super.onBackPressed()
    }

    private fun displayPicture(picture: Picture, nextPicture: Picture?, prevPicture: Picture?) {
        // Preload next and previous images with a size limit and cache strategy
        nextPicture?.let {
            Glide.with(this)
                .load(it.fileUri)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized images
                .preload()
        }
        prevPicture?.let {
            Glide.with(this)
                .load(it.fileUri)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized images
                .preload()
        }

        // Load current image with a cache strategy
        Glide.with(this)
            .load(picture.fileUri)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized images
            .signature(ObjectKey(System.currentTimeMillis())) // Add unique signature
            .into(photoView)
    }


    private fun startCropping(uri: Uri) {
        val options = UCrop.Options().apply {
            setHideBottomControls(true)
            setFreeStyleCropEnabled(true)
        }
        val uCropIntent = UCrop.of(uri, uri)
            .withAspectRatio(1F, 1F)
            .withMaxResultSize(1080, 1080)
            .withOptions(options)
            .getIntent(this@DescriptionActivity)

        uCropLauncher.launch(uCropIntent)

        CoroutineScope(Dispatchers.IO).launch {
            val backupUri = createBackupUri(uri)

            if (backupUri != null) {
                // Run copyFile in a coroutine with Dispatchers.IO

                copyFile(this@DescriptionActivity, uri, backupUri)
            }
            else {
            // Handle the case where the backupUri couldn't be created
            }
        }
    }



    fun getBackupUri(uri: Uri): Uri? {
        val parentDocumentFile = DocumentFile.fromTreeUri(this, uri)
        val originalSubfolder = parentDocumentFile?.findFile("original")

        val backupFileName = "${DocumentFile.fromSingleUri(this, uri)?.name}"
        val backupDocumentFile = originalSubfolder?.findFile(backupFileName)

        return backupDocumentFile?.uri
    }

    fun createBackupUri(uri: Uri): Uri? {
        val backupUri = getBackupUri(uri)
        if (backupUri != null) {
            // If the backup file already exists, don't overwrite it
            return null
        }

        val parentDocumentFile = DocumentFile.fromTreeUri(this, uri)
        val originalSubfolder = parentDocumentFile?.findFile("original")
            ?: parentDocumentFile?.createDirectory("original")

        val backupFileName = "${DocumentFile.fromSingleUri(this, uri)?.name}"
        val backupDocumentFile = originalSubfolder?.createFile("image/*", backupFileName)

        return backupDocumentFile?.uri
    }

    fun copyFile(context: Context, sourceUri: Uri, destinationUri: Uri) {
        try {
            context.contentResolver.openInputStream(sourceUri).use { inputStream ->
                context.contentResolver.openOutputStream(destinationUri).use { outputStream ->
                    if (inputStream != null && outputStream != null) {
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val uCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultIntent = result.data
            val resultUri = resultIntent?.let { UCrop.getOutput(it) }

            if (resultUri == null) {
                return@registerForActivityResult
            }

            // Load the cropped image
            Glide.with(this)
                .load(resultUri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(photoView)

            val picture = descriptionViewModel.currentPicture.value
            picture?.let {
                // Overwrite the original file with the cropped image
                val croppedData = contentResolver.openInputStream(resultUri)?.use {
                    it.readBytes()
                }
                // write the data back to the original file
                try {
                    contentResolver.openOutputStream(it.fileUri)?.use {
                        it.write(croppedData)
                    }
                } catch (e: IOException) {
                    Log.e("DescriptionActivity", "Error writing cropped image data", e)
                }

                // Regenerate the thumbnail
                // Launch a coroutine scope
                CoroutineScope(Dispatchers.IO).launch {
                    val picture = descriptionViewModel.currentPicture.value
                    picture?.let {
                        // Call the suspend function within the coroutine scope
                        picturesRepository.generateThumbnail(it.fileUri, true)
                    }
                }

                // Refresh the displayed image
                val nextPicture = descriptionViewModel.getNextPicture()
                val prevPicture = descriptionViewModel.getPreviousPicture()
                displayPicture(it, nextPicture, prevPicture)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val resultIntent = result.data
            val cropError = resultIntent?.let { UCrop.getError(it) }
            Log.e("uCrop", "Crop error: $cropError")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_description, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_restore_original -> {
                restoreOriginalImage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun restoreOriginalImage() {
        val picture = descriptionViewModel.currentPicture.value
        picture?.let {
            val originalFileExtension = MimeTypeMap.getFileExtensionFromUrl(it.fileUri.toString())
            val backupUri = getBackupUri(it.fileUri)
            Log.d("LL", backupUri.toString())

            if (backupUri != null) {
                copyFile(this, backupUri, it.fileUri)
            } else {
                // Handle the case where the backupUri couldn't be found
            }
            // Regenerate the thumbnail
            // Launch a coroutine scope
            CoroutineScope(Dispatchers.IO).launch {
                val picture = descriptionViewModel.currentPicture.value
                picture?.let {
                    // Call the suspend function within the coroutine scope
                    picturesRepository.generateThumbnail(it.fileUri, true)
                }
            }
            // Refresh the displayed image
            val nextPicture = descriptionViewModel.getNextPicture()
            val prevPicture = descriptionViewModel.getPreviousPicture()
            displayPicture(it, nextPicture, prevPicture)
        }
    }


}