package com.shankar.tgp_arcore.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.shankar.tgp_arcore.R

class NavigationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
    }


    fun onItemClick(view : View) {

        val intent = when(view.id) {
            R.id.scene_form -> {
                Intent(this, SceneFormActivity::class.java)
            }
            R.id.ar_core -> {
                Intent(this, GalleryActivity::class.java)
            }

            else -> {
                Intent(this, SceneFormActivity::class.java)
            }
        }

        startActivity(intent)
    }
}