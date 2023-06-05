package com.tools.phone_captions.models

import android.net.Uri

interface Image {
    val fileUri: Uri
    var description: String
    var isSelected: Boolean
}

data class Picture(
    override val fileUri: Uri,
    override var description: String,
    override var isSelected: Boolean = false // This field is for handling selection
) : Image

data class Thumbnail(
    override val fileUri: Uri,
    override var description: String,
    val originalPictureUri: Uri,
    override var isSelected: Boolean = false // This field is for handling selection
) : Image