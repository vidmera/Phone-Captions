package com.tools.phone_captions.utils

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.tools.phone_captions.R
import com.tools.phone_captions.models.Image
import java.io.File

class ImageAdapter(private val context: Context) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
    private var images = mutableListOf<Image>()
    private var selectedPositions = mutableSetOf<Int>()

    inner class ImageViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image)

        fun bind(image: Image) {
            val requestBuilder = Glide.with(context)
                .load(image.fileUri)
                .thumbnail(0.1f)

            // Enable cache control based on modification timestamp
            requestBuilder.signature(ObjectKey(File(image.fileUri.path).lastModified()))

            // Enable cache invalidation to always fetch the latest version
            requestBuilder.skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)

            requestBuilder.into(imageView)

            if (image.description.isEmpty()) {
                // If no description, set red border
                imageView.setBackgroundResource(R.drawable.red_border)
            } else {
                // Otherwise, clear the border
                imageView.setBackgroundResource(0)
            }

            // Change the appearance of the selected image
            if (image.isSelected) {
                imageView.setColorFilter(ContextCompat.getColor(context, R.color.selection_overlay))
            } else {
                imageView.clearColorFilter()
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val imageView = LayoutInflater.from(parent.context).inflate(R.layout.recycler_item, parent, false) as ImageView
        return ImageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        holder.bind(image)
        holder.imageView.setOnClickListener {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
                images[position].isSelected = false
            } else {
                selectedPositions.add(position)
                images[position].isSelected = true
            }
            notifyItemChanged(position)
        }
    }

    fun updateImages(newImages: List<Image>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    /*
    fun updateImagesWithDescriptions(newImages: List<Thumbnail>) {
        for (newImage in newImages) {
            val position = images.indexOfFirst { it.fileUri == newImage.fileUri }
            if (position != -1 && images[position].description != newImage.description) {
                images[position].description = newImage.description
                notifyItemChanged(position)
            }
        }
    }*/

    override fun getItemCount() = images.size

}


class SpacesItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = space
        outRect.right = space
        outRect.bottom = space

        // Add top margin only for the first item to avoid double space between items
        if (parent.getChildLayoutPosition(view) == 0) {
            outRect.top = space
        } else {
            outRect.top = 0
        }
    }
}