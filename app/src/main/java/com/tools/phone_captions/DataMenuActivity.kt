package com.tools.phone_captions

import android.annotation.SuppressLint
import android.content.Intent
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import com.tools.phone_captions.utils.ImageAdapter
import com.tools.phone_captions.viewmodels.DataMenuViewModel
import com.tools.phone_captions.utils.SpacesItemDecoration
import com.tools.phone_captions.utils.ZipWorker
import com.tools.phone_captions.viewmodels.ViewModelFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.zip.ZipInputStream

class DataMenuActivity : AppCompatActivity() {
    private lateinit var zipFilePickerLauncher: ActivityResultLauncher<String>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>
    private lateinit var factory: ViewModelFactory
    private lateinit var viewModel: DataMenuViewModel
    private var pictureDirectory: DocumentFile? = null
    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_menu)
        progressBar = findViewById(R.id.progress_bar)


        val folderUri: Uri = intent.getParcelableExtra("folderUri")!!
        (application as PhoneCaptionsApplication).initializePicturesRepository(folderUri)
        val picturesRepository = (application as PhoneCaptionsApplication).picturesRepository
        factory = ViewModelFactory(picturesRepository!!)
        viewModel = ViewModelProvider(this, factory).get(DataMenuViewModel::class.java)



        folderUri?.let { uri ->
            pictureDirectory = DocumentFile.fromTreeUri(this, uri)
            // Use the documentFile for further operations
        }

        toolbar = findViewById(R.id.toolbar)
        val folderName = pictureDirectory?.name?.substringAfterLast("/")
        toolbar.title = folderName
        setSupportActionBar(toolbar)

        // Create and display thumbnails of the pictures at the top of the screen
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.spacing)
        recyclerView.addItemDecoration(SpacesItemDecoration(spacingInPixels))

        // We attach an adapter, to prevent trying it to layout its items before
        // an adapter is set on it
        val imageAdapter = ImageAdapter(this)
        recyclerView.adapter = imageAdapter

        viewModel.filteredThumbnails.observe(this, Observer { thumbnails ->
            // Display the filtered list
            if (thumbnails != null) {
                imageAdapter.updateImages(thumbnails)
                imageAdapter.notifyDataSetChanged()
            }
        })

        zipFilePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { zipFileUri: Uri? ->
            // Unzip the file and save it to pictureDirectory
            zipFileUri?.let { uri ->
                val zipInputStream = contentResolver.openInputStream(uri)?.let { ZipInputStream(it) }

                var entry = zipInputStream?.nextEntry
                while (entry != null) {
                    // Determine MIME type based on file extension
                    val mimeType = when (entry.name.substringAfterLast(".")) {
                        "txt" -> "text/plain"
                        "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
                        else -> "application/octet-stream"
                    }

                    if (entry.isDirectory) {
                        // Create directory
                        val directoryUri = DocumentsContract.createDocument(
                            contentResolver,
                            pictureDirectory!!.uri,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            entry.name
                        )
                    } else {
                        // Create file
                        val createdFileUri = DocumentsContract.createDocument(
                            contentResolver,
                            pictureDirectory!!.uri,
                            mimeType,
                            entry.name
                        )

                        // Write InputStream to Uri
                        val outputStream = contentResolver.openOutputStream(createdFileUri!!)
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (zipInputStream!!.read(buffer).also { len = it } != -1) {
                            outputStream?.write(buffer, 0, len)
                        }
                        outputStream?.close()
                    }
                    entry = zipInputStream?.nextEntry
                }
                zipInputStream?.closeEntry()
                zipInputStream?.close()
            }
            refreshView()
        }


        filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { fileUris: List<Uri>? ->
            fileUris?.let { uris ->
                if (uris.isNotEmpty()) {
                    for (contentUri in uris) {
                        val copiedFilePath = picturesRepository.copyFileToPictureDirectory(
                            contentUri,
                            pictureDirectory!!
                        )
                        // Now copiedFilePath is the path of the file that has been copied to pictureDirectory
                        // If you need to do something with the copied file, you can use copiedFilePath here
                    }
                    refreshView()
                }
            }
        }

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folderUri: Uri? ->
            // Use the Uri object to access the files in the directory
            if (folderUri != null) {
                val documentFile = DocumentFile.fromTreeUri(this, folderUri)
                documentFile?.let {
                    val children = it.listFiles()
                    children?.forEach { child ->
                        if (child.isFile && child.canRead()) {
                            picturesRepository.copyFileToPictureDirectory(child.uri, pictureDirectory!!)
                        }
                    }
                }
                refreshView()
            }
        }

        fun editDescription(view: View) {
            val selectedThumbnails = if (viewModel.thumbnails.value?.none { it.isSelected } == true) {
                viewModel.filteredThumbnails.value
            } else {
                viewModel.thumbnails.value?.filter { it.isSelected }
            }
            if (selectedThumbnails != null) {
                if (selectedThumbnails.isNotEmpty()) {
                    // Pass the selected images' original URIs to the DescriptionActivity, and the folderUri
                    val intent = Intent(this, DescriptionActivity::class.java)
                    intent.putExtra("folderUri", folderUri)
                    intent.putParcelableArrayListExtra(
                        "selectedPicturesUri",
                        ArrayList(selectedThumbnails.map { it.originalPictureUri })
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val editDescriptionButton: Button = findViewById(R.id.edit_description_button)
        editDescriptionButton.setOnClickListener { view ->
            editDescription(view)
        }

        val filterUntaggedButton: Button = findViewById(R.id.middle_button)
        filterUntaggedButton.text = "" +
                "Untagged"

        filterUntaggedButton.setOnClickListener {
            val currentlyShowingUntagged = viewModel.showNoDescriptionOnly.value ?: false
            viewModel.setShowUntaggedOnly(!currentlyShowingUntagged)
            filterUntaggedButton.text = if (!currentlyShowingUntagged) "All" else "Untagged"
        }

        val exportButton: Button = findViewById(R.id.right_button)
        exportButton.text = "Export"
        exportButton.setOnClickListener {
            createZipFile()
        }

    }

    private fun refreshView(){
        viewModel.viewModelScope.launch {
            progressBar.visibility = View.VISIBLE
            viewModel.refreshFolderDatabase(pictureDirectory!!.uri)
            loadThumbnails()
            progressBar.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_data, menu)

        // Get the search item and set up the search view
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.queryHint = "Search descriptions..."

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateFilter(newText ?: "")
                return true
            }
        })

        searchView.setOnCloseListener {
            searchView.setQuery("", false)
            viewModel.updateFilter("")
            true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume() {
        // If we are coming from Description activity, we wait until the description is updated
        // (or maximum 50ms)
        super.onResume()
        viewModel.setUpdateThumbnails(true)

        val app = applicationContext as PhoneCaptionsApplication
        if (app.isBackButtonPressed) {
            lifecycleScope.launchWhenResumed {
                try {
                    withTimeout(50L) {
                        app.descriptionUpdatedFlow.first()
                    }
                } catch (e: TimeoutCancellationException) {
                    // Handle the timeout case if needed
                }
                loadThumbnails()
                app.isBackButtonPressed = false
            }
        } else {
            loadThumbnails()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_import_directory -> {
                // Import images from a directory
                folderPickerLauncher.launch(null)
                return true
            }
            R.id.menu_import_zip_file -> {
                // Import and unzip a zip file
                zipFilePickerLauncher.launch("application/zip")
                return true
            }
            R.id.menu_import_files -> {
                // Import one or multiple selected files
                filePickerLauncher.launch(arrayOf("image/*"))
                return true
            }
            R.id.menu_refresh -> {
                // Refresh the database
                refreshView()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun createZipFile() {
        val zipFileName = "${pictureDirectory?.name ?: "pictures"}.zip"

        // Check if the zip file already exists
        val existingZipFile = pictureDirectory?.findFile(zipFileName)

        // If the zip file exists, delete it
        if (existingZipFile != null) {
            DocumentsContract.deleteDocument(contentResolver, existingZipFile.uri)
        }

        // Create a new zip file
        val createdFileUri = DocumentsContract.createDocument(
            contentResolver,
            pictureDirectory!!.uri,
            "application/zip",
            zipFileName
        )

        if (createdFileUri == null) {
            Toast.makeText(this, "Failed to create zip file", Toast.LENGTH_SHORT).show()
            return
        }

        val children = pictureDirectory?.listFiles()
        if (children == null || children.isEmpty()) {
            Toast.makeText(this, "No files to export", Toast.LENGTH_SHORT).show()
            return
        }

        val workManager = WorkManager.getInstance(this)
        val inputData = Data.Builder()
            .putString("zip_uri", createdFileUri.toString())
            .putString("folder_uri", pictureDirectory?.uri.toString())
            .build()
        val zipWorkerRequest = OneTimeWorkRequestBuilder<ZipWorker>()
            .setInputData(inputData)
            .build()
        workManager.enqueue(zipWorkerRequest)

        Toast.makeText(this, "Exporting to $zipFileName", Toast.LENGTH_SHORT).show()
    }

    private fun loadThumbnails(){
        // we can add log and performance checks on the on complete
        viewModel.loadThumbnails(pictureDirectory!!.uri) {}

    }

}
