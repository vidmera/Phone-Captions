package com.tools.phone_captions.viewmodels

import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.tools.phone_captions.data.PicturesRepository
import com.tools.phone_captions.models.Thumbnail
import kotlinx.coroutines.*

class DataMenuViewModel(private val picturesRepository: PicturesRepository) : ViewModel() {

    val updateThumbnails = MutableLiveData<Boolean>(false)

    init {
        viewModelScope.launch {
            val folderUri = picturesRepository.getFolderUri()
            if (folderUri != null){
                picturesRepository.ensureAllThumbnailsExist(folderUri)
            }
        }
    }

    val showNoDescriptionOnly = MutableLiveData<Boolean>(false)
    private val _thumbnails = MutableLiveData<List<Thumbnail>>()
    val thumbnails: LiveData<List<Thumbnail>> = _thumbnails

    val descriptionQuery = MutableLiveData<String>("")
    val filteredThumbnails: LiveData<List<Thumbnail>> = MediatorLiveData<List<Thumbnail>>().apply {
        var lastThumbnails: List<Thumbnail> = emptyList()
        var lastDescriptionQuery: String = ""
        var showUntaggedOnly = false

        val applyFilter: () -> Unit = {
            value = when {
                lastDescriptionQuery.isEmpty() && !showUntaggedOnly -> {
                    lastThumbnails // This line will return all thumbnails if the query is empty
                }
                lastDescriptionQuery.isEmpty() && showUntaggedOnly -> {
                    lastThumbnails.filter { it.description.isEmpty() }
                }
                !lastDescriptionQuery.isEmpty() && !showUntaggedOnly -> {
                    lastThumbnails.filter { thumbnail ->
                        thumbnail.description.contains(lastDescriptionQuery, ignoreCase = true)
                    }
                }
                else -> {
                    lastThumbnails.filter { thumbnail ->
                        thumbnail.description.isEmpty() &&
                                thumbnail.description.contains(lastDescriptionQuery, ignoreCase = true)
                    }
                }
            }
        }

        addSource(_thumbnails) { thumbnails ->
            lastThumbnails = thumbnails
            applyFilter()
        }

        addSource(descriptionQuery) { query ->
            lastDescriptionQuery = query
            applyFilter()
        }

        addSource(showNoDescriptionOnly) { shouldShowUntagged ->
            showUntaggedOnly = shouldShowUntagged ?: false
            applyFilter()
        }
    }

    suspend fun refreshFolderDatabase(folderUri: Uri) {
        picturesRepository.refreshFolderDatabase(folderUri)
    }

    fun logThumbnails(folderUri: Uri) {
        Log.d("ThumbnailLog", folderUri.toString())
        viewModelScope.launch {
            val thumbnails = picturesRepository.getFolderThumbnails(folderUri)
            thumbnails.forEach { thumbnail ->
                Log.d("ThumbnailLog", "ImageFileUri: ${thumbnail.imageUri}, TxtFileUri: ${thumbnail.txtUri}, Description: ${thumbnail.description}")
            }
        }
    }

    fun setShowUntaggedOnly(shouldShow: Boolean) {
        showNoDescriptionOnly.value = shouldShow
    }

    fun loadThumbnails(folderUri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {

            picturesRepository.ensureAllThumbnailsExist(folderUri)
            val thumbnailsList = picturesRepository.getThumbnails(folderUri)


            _thumbnails.postValue(thumbnailsList)
            descriptionQuery.value = descriptionQuery.value

            onComplete()
        }

    }

    fun updateFilter(query: String) {
        this.descriptionQuery.value = query
    }

    fun setUpdateThumbnails(value: Boolean) {
        updateThumbnails.value = value
    }
}

class ViewModelFactory(private val picturesRepository: PicturesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DataMenuViewModel::class.java) -> {
                DataMenuViewModel(picturesRepository) as T
            }
            modelClass.isAssignableFrom(DescriptionViewModel::class.java) -> {
                DescriptionViewModel(picturesRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}