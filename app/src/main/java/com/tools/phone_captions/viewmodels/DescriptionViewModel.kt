package com.tools.phone_captions.viewmodels

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tools.phone_captions.data.PicturesRepository
import com.tools.phone_captions.models.Picture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class DescriptionViewModel(private val picturesRepository: PicturesRepository) : ViewModel() {

    private var pictures: List<Picture> = listOf()
    private var currentPosition: Int = 0

    val currentPicture: LiveData<Picture> get() = _currentPicture
    private val _currentPicture = MutableLiveData<Picture>()

    fun loadPictures(fileUris: List<Uri>) {
        this.pictures = picturesRepository.getPictures(fileUris)
        _currentPicture.value = pictures.getOrNull(currentPosition)
    }

    fun setCurrentPosition(position: Int) {
        if (pictures.isNotEmpty() && position in pictures.indices) {
            currentPosition = position
            _currentPicture.value = pictures[position]
        }
    }

    fun moveNext() {
        if (currentPosition < pictures.size - 1) {
            currentPosition++
            _currentPicture.value = pictures[currentPosition]
        }
    }

    fun movePrevious() {
        if (currentPosition > 0) {
            currentPosition--
            _currentPicture.value = pictures[currentPosition]
        }
    }

    fun saveDescription(description: String) {
        val picture = _currentPicture.value ?: return
        picture.description = description
        viewModelScope.launch(Dispatchers.IO) {
            picturesRepository.saveDescription(picture)
        }
    }

    fun getNextPicture(): Picture? {
        return if (currentPosition!! < pictures.size - 1) pictures[currentPosition!! + 1] else null
    }

    fun getPreviousPicture(): Picture? {
        return if (currentPosition!! > 0) pictures[currentPosition!! - 1] else null
    }

}