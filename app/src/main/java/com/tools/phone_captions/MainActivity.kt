package com.tools.phone_captions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private lateinit var chooseFolderLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnChooseFolder = findViewById<Button>(R.id.btnChooseFolder)

        chooseFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { folderUri ->
                // request Persistable permission
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(folderUri, takeFlags)

                // Pass the selected folder URI to the DataMenuActivity activity
                val intent = Intent(this, DataMenuActivity::class.java)
                intent.putExtra("folderUri", folderUri)

                val directoryDocument = DocumentFile.fromTreeUri(this, uri)

                // Check if the .nomedia file already exists
                val existingNoMediaFile = directoryDocument?.findFile(".nomedia")

                // Create the .nomedia file only if it does not exist
                if (existingNoMediaFile == null) {
                    directoryDocument?.createFile("application/octet-stream", ".nomedia")
                }

                startActivity(intent)
            }
        }

        btnChooseFolder.setOnClickListener {
            // it seems we don't need the storage permission with SAF
            if (hasStoragePermission()) {
                openFolderPicker()
            } else {
                openFolderPicker()
                //requestStoragePermission()
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun openFolderPicker() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        chooseFolderLauncher.launch(null)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}
