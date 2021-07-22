package com.shankar.tgp_arcore.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.shankar.tgp_arcore.adapter.GalleryAdapter
import com.shankar.tgp_arcore.data.GalleryModel
import com.shankar.tgp_arcore.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter : GalleryAdapter

    private val imagesList : ArrayList<GalleryModel> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }
    init {
        initViews()
        addImagesToList()
    }

    private fun addImagesToList() {
        imagesList.add(galleryModel("IMG_ONE", "Monalisa"))
        imagesList.add(galleryModel("IMG_ONE", "Monalisa"))
        imagesList.add(galleryModel("IMG_ONE", "Monalisa"))
        imagesList.add(galleryModel("IMG_ONE", "Monalisa"))
        imagesList.add(galleryModel("IMG_ONE", "Monalisa"))
//        imagesList.add(galleryModel(IMG_ONE, "Monalisa"))
//        imagesList.add(galleryModel(IMG_ONE, "Monalisa"))
//        imagesList.add(galleryModel(IMG_ONE, "Monalisa"))
//        imagesList.add(galleryModel(IMG_ONE, "Monalisa"))
//        imagesList.add(galleryModel(IMG_ONE, "Monalisa"))

        adapter.notifyDataSetChanged()
    }

    private fun galleryModel(url : String, title : String) : GalleryModel {
        return GalleryModel(url, title)
    }

    private fun initViews() {

        adapter = GalleryAdapter(imagesList)

        binding.galleryRecycler.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 2, GridLayoutManager.VERTICAL, false)
            adapter = adapter
        }

    }
}