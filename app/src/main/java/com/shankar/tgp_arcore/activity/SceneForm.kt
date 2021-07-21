package com.shankar.tgp_arcore.activity

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import com.google.android.filament.ColorGrading
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.Sceneform
import com.google.ar.sceneform.rendering.EngineInstance
import com.google.ar.sceneform.rendering.FixedHeightViewSizer
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.rendering.ViewSizer
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.shankar.tgp_arcore.R
import com.shankar.tgp_arcore.databinding.ActivitySceneFormBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.lang.Exception

class SceneForm : AppCompatActivity(), FragmentOnAttachListener,
    BaseArFragment.OnTapArPlaneListener,
    BaseArFragment.OnSessionConfigurationListener,
    ArFragment.OnViewCreatedListener {

    private lateinit var arFragment: ArFragment
    private var viewRenderable: ViewRenderable? = null

    private lateinit var binding: ActivitySceneFormBinding

    private lateinit var anchor: Anchor
    private lateinit var anchorNode: AnchorNode
    private var isExists = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySceneFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.addFragmentOnAttachListener(this)

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                supportFragmentManager.beginTransaction()
                    .add(R.id.arFragment, ArFragment::class.java, null)
                    .commit()
            }
        }


        loadModels()

    }

    private fun loadModels() {

        val v: View = layoutInflater.inflate(R.layout.layout_art, null)
        val imageView = v.findViewById<View>(R.id.art) as ImageView

        Picasso.get()
            .load("https://images.all-free-download.com/images/graphiclarge/mona_lisa_painting_art_214707.jpg")
            .into(imageView, object : Callback {
                override fun onSuccess() {
                    buildModel(v)
                    Toast.makeText(applicationContext, "Image Download success", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onError(e: Exception?) {
                    Toast.makeText(
                        applicationContext,
                        "${e!!.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()

                }

            })


    }

    private fun buildModel(v: View) {

        v.requestLayout()
        ViewRenderable.builder()
            .setView(this, R.layout.layout_art)
            .build()
            .thenAccept { renderable: ViewRenderable ->
                viewRenderable = renderable
            }
    }


    override fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        if (fragment.id == R.id.arFragment) {
            arFragment = fragment as ArFragment
            arFragment.setOnSessionConfigurationListener(this)
            arFragment.setOnViewCreatedListener(this)
            arFragment.setOnTapArPlaneListener(this)
        }

    }

    override fun onTapPlane(hitResult: HitResult?, plane: Plane?, motionEvent: MotionEvent?) {
        if (viewRenderable == null) {
            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
            return
        }


        
        if (!isExists) {
            // Create the Anchor.
            val session = arFragment.arSceneView.session
            anchor = hitResult!!.createAnchor()
            anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)


            // Create the transformable model and add it to the anchor.
            val painting = TransformableNode(arFragment.transformationSystem)
            painting.renderable = viewRenderable


            anchorNode.addChild(painting)
            isExists = true
        }


    }

    override fun onSessionConfiguration(session: Session?, config: Config?) {
        if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config!!.depthMode = Config.DepthMode.AUTOMATIC
        }
    }

    override fun onViewCreated(arFragment: ArFragment?, arSceneView: ArSceneView?) {

        // Currently, the tone-mapping should be changed to FILMIC
        // because with other tone-mapping operators except LINEAR
        // the inverseTonemapSRGB function in the materials can produce incorrect results.
        // The LINEAR tone-mapping cannot be used together with the inverseTonemapSRGB function.
        val renderer = arSceneView!!.renderer

        if (renderer != null) {
            renderer.filamentView.colorGrading = ColorGrading.Builder()
                .toneMapping(ColorGrading.ToneMapping.FILMIC)
                .build(EngineInstance.getEngine().filamentEngine)
        }

        // Fine adjust the maximum frame rate

        // Fine adjust the maximum frame rate
        arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)

    }
}