package com.shankar.tgp_arcore.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.shankar.tgp_arcore.R
import com.shankar.tgp_arcore.activity.SceneFormActivity
import com.shankar.tgp_arcore.data.GalleryModel
import com.shankar.tgp_arcore.databinding.ItemGalleryBinding


class GalleryAdapter(private val imagesList: ArrayList<GalleryModel>) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    class ViewHolder(itemBinding: ItemGalleryBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        val binding = itemBinding
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.item_gallery,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.model = imagesList[position]

        holder.binding.augmentedReality.setOnClickListener {
            val intent = Intent(it.context, SceneFormActivity::class.java)
            val gson = Gson()
            val jsonList: String = gson.toJson(imagesList[position])
            intent.putExtra("jsonModel", jsonList)
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return imagesList.size
    }
}