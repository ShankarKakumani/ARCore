package com.shankar.tgp_arcore.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.transition.Scene
import android.view.View
import com.shankar.tgp_arcore.R

class Navigation : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
    }


    fun onItemClick(view : View) {

        val intent = when(view.id) {
            R.id.scene_form -> {
                Intent(this, SceneForm::class.java)
            }
            R.id.ar_core -> {
                Intent(this, MainActivity::class.java)
            }

            R.id.ar_core_two -> {
                Intent(this, TwoD::class.java)

            }
            else -> {
                Intent(this, SceneForm::class.java)
            }
        }

        startActivity(intent)
    }
}