package com.shankar.tgp_arcore.data

import com.shankar.tgp_arcore.R

data class GalleryModel(
    val imageUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    var frameColor: Int = R.color.white,
    val title: String = ""
)
