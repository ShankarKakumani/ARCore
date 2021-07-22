package com.shankar.tgp_arcore.activity

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import com.google.android.filament.ColorGrading
import com.google.ar.core.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.EngineInstance
import com.google.ar.sceneform.rendering.PlaneRenderer
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import com.shankar.tgp_arcore.R
import com.shankar.tgp_arcore.databinding.ActivitySceneFormBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.util.concurrent.CompletableFuture


class SceneForm : AppCompatActivity(), FragmentOnAttachListener,
    BaseArFragment.OnTapArPlaneListener,
    BaseArFragment.OnSessionConfigurationListener,
    ArFragment.OnViewCreatedListener {

    private lateinit var arFragment: ArFragment
    private var artRenderable: ViewRenderable? = null

    private lateinit var binding: ActivitySceneFormBinding

    private lateinit var anchor: Anchor
    private lateinit var anchorNode: AnchorNode
    private lateinit var artNode : Node

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


        buildModel(300, 460)

    }

    private fun changePlaneRendererTexture() {
        // Build texture sampler
        val sampler: Texture.Sampler = Texture.Sampler.builder()
            .setMinFilter(Texture.Sampler.MinFilter.LINEAR)
            .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
            .setWrapMode(Texture.Sampler.WrapMode.REPEAT).build()

        // Build texture with sampler

        val trigrid: CompletableFuture<Texture> = Texture.builder()
            .setSource(this, R.drawable.red_trigrid)
            .setSampler(sampler).build()

        // Set plane texture
        arFragment.arSceneView
            .planeRenderer
            .material
            .thenAcceptBoth(trigrid) { material, texture ->
                material.setTexture(
                    PlaneRenderer.MATERIAL_TEXTURE,
                    texture
                )
            }
    }


    private fun buildModel(width : Int, height : Int) {

        val view: View = layoutInflater.inflate(R.layout.layout_art, null)
        val imageView = view.findViewById<View>(R.id.art) as ImageView


        Picasso.get()
            .load("https://images.all-free-download.com/images/graphiclarge/mona_lisa_painting_art_214707.jpg")
            .into(imageView, object : Callback {
                override fun onSuccess() {

                    view.layoutParams = ViewGroup.LayoutParams(width, height)

                    ViewRenderable.builder()
                        .setView(this@SceneForm, view)
                        .build()
                        .thenAccept { renderable: ViewRenderable ->
                            artRenderable = renderable
                        }
                }

                override fun onError(e: Exception?) {

                }

            })

    }


    override fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        //1st
        if (fragment.id == R.id.arFragment) {
            arFragment = fragment as ArFragment
            arFragment.setOnSessionConfigurationListener(this)
            arFragment.setOnViewCreatedListener(this)
            arFragment.setOnTapArPlaneListener(this)


        }

    }

    override fun onTapPlane(hitResult: HitResult?, plane: Plane?, motionEvent: MotionEvent?) {
        if (artRenderable == null) {
            Toast.makeText(this, "Model is Null...", Toast.LENGTH_SHORT).show()
            return
        }


        setModelOnPlane(hitResult, plane, motionEvent)
    }

    private fun setModelOnPlane(hitResult: HitResult?, plane: Plane?, motionEvent: MotionEvent?) {

        if (!isExists) {

            //To hide plane dots
//            arFragment.arSceneView.planeRenderer.isVisible = false
//            changePlaneRendererTexture()

            // Create the Anchor.
            anchor = hitResult!!.createAnchor()
            anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            //to turn off shadows
            artRenderable?.isShadowReceiver = false
            artRenderable?.isShadowCaster = false


            artNode = Node()
            artNode.renderable = artRenderable
            anchorNode.addChild(artNode)

            //to make the art visible correctly on Vertical wall
            if (plane!!.type == Plane.Type.VERTICAL) {
                artNode.setLookDirection(Vector3.forward())
            }
            isExists = true
        }
        else
        {
            anchorNode.removeChild(artNode)
            isExists = false

            val widthArray = arrayListOf(150,200,300,400)

            val width = widthArray.random()

            buildModel(width, width +100)
            setModelOnPlane(hitResult, plane, motionEvent)

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
        arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)


    }
}