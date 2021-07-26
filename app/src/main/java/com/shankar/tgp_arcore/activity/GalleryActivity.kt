package com.shankar.tgp_arcore.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.shankar.tgp_arcore.R
import com.shankar.tgp_arcore.adapter.GalleryAdapter
import com.shankar.tgp_arcore.data.GalleryModel
import com.shankar.tgp_arcore.databinding.ActivityGalleryBinding

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var galleryAdapter : GalleryAdapter

    private val imagesList : ArrayList<GalleryModel> = ArrayList()

    val IMG_1 = "https://webneel.com/daily/sites/default/files/images/daily/11-2012/Leonid%20Afremov%20color%20(28).jpg"
    val IMG_2 = "https://miro.medium.com/max/1400/1*o1mFGQr5CCiePDEXNBzhow@2x.jpeg"
    val IMG_3 = "https://i.pinimg.com/originals/b4/67/e8/b467e8286938b90f0536ee90c7b07317.jpg"
    val IMG_4 = "https://www.e-bousquet.com/mediatheque/3/le_toit_du_monde_peinture_eliora_bousquet.jpg"
    val IMG_5 = "https://i1.wp.com/detechter.com/wp-content/uploads/2014/12/Girl_With_A_Pearl_Earring_Famous-Paintings-in-the-World.jpg?fit=715%2C1023&ssl=1"
    val IMG_6 = "https://wisetoast.com/wp-content/uploads/2015/10/Rembrandt-Christ-in-the-Storm-on-the-Lake-of-Galilee.jpg"
    val IMG_7 = "https://www.myindianart.com/uploads/1441111143dscn2363.jpg"
    val IMG_8 = "https://www.virtosuart.com/images/2015/paint/C-1-2015-034-Che-Guevara.JPG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initViews()
        addImagesToList()

    }


    private fun addImagesToList() {
        imagesList.add(GalleryModel(IMG_1, 450, 350, R.color.white,"Girl with Umbrella"))
        imagesList.add(GalleryModel(IMG_2, 350, 490, R.color.green_400,"Couple Hug"))
        imagesList.add(GalleryModel(IMG_3, 340, 500, R.color.white,"Yellow Couple Kiss"))
        imagesList.add(GalleryModel(IMG_4, 400, 500, R.color.white,"Paint Splash"))
        imagesList.add(GalleryModel(IMG_5, 340, 500, R.color.white,"Chinese Women"))
        imagesList.add(GalleryModel(IMG_6, 340, 550, R.color.white,"Boat, Sea, Rain"))
        imagesList.add(GalleryModel(IMG_7, 330, 450, R.color.white,"Yellow '4' paint"))
        imagesList.add(GalleryModel(IMG_8, 330, 500, R.color.white,"Face, Background red/black"))
        galleryAdapter.notifyDataSetChanged()
    }


    private fun initViews() {

        galleryAdapter = GalleryAdapter(imagesList)

        binding.galleryRecycler.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 2, GridLayoutManager.VERTICAL, false)
            adapter = galleryAdapter
        }

    }
}