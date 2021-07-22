package com.shankar.tgp_arcore.adapter

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.squareup.picasso.Picasso

object BindingAdapter {

    @BindingAdapter(value = ["app:loadImage"])
    @JvmStatic
    fun setDrawableImage(view: ImageView, imageUrl: String) {

        Picasso.get().load(imageUrl).into(view)
    }

}