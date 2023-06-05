package com.tools.phone_captions

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.tools.phone_captions.data.PicturesRepository
import com.tools.phone_captions.entities.AppDatabase
import kotlinx.coroutines.flow.MutableSharedFlow

class PhoneCaptionsApplication : Application() {
    var picturesRepository: PicturesRepository? = null
    val descriptionUpdatedFlow = MutableSharedFlow<Unit>()
    var isBackButtonPressed: Boolean = false

    companion object {
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, AppDatabase::class.java, "thumbnail-db").build()
    }

    fun initializePicturesRepository(folderUri: Uri) {
        picturesRepository = PicturesRepository(this, folderUri)
    }
}