package com.shankar.tgp_arcore.activity

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.MutableLiveData
import com.google.android.filament.ColorGrading
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.EngineInstance
import com.google.ar.sceneform.rendering.PlaneRenderer
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.gson.Gson
import com.shankar.tgp_arcore.R
import com.shankar.tgp_arcore.data.GalleryModel
import com.shankar.tgp_arcore.databinding.ActivitySceneFormBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer


open class SceneFormActivity : AppCompatActivity(), FragmentOnAttachListener,
    BaseArFragment.OnTapArPlaneListener,
    BaseArFragment.OnSessionConfigurationListener,
    ArFragment.OnViewCreatedListener {

    private lateinit var arFragment: ArFragment
    private var artRenderable = MutableLiveData<ViewRenderable>()

    private lateinit var binding: ActivitySceneFormBinding

    private lateinit var anchor: Anchor
    private lateinit var anchorNode: AnchorNode
    private lateinit var artNode: TransformableNode

    private lateinit var hitResult: HitResult
    private lateinit var plane: Plane
    private lateinit var motionEvent: MotionEvent

    private lateinit var galleryModel: GalleryModel

    private val onChosen = MutableLiveData(false)
    private var isExists = false
    private val isDotsVisible = MutableLiveData(true)
    private val isCameraEnabled = MutableLiveData(false)
    private val isScalable = MutableLiveData(false)
    private val isDraggable = MutableLiveData(false)
    val gson = Gson()

    lateinit var alertDialog: AlertDialog


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySceneFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.lifecycleOwner = this


        handleIntent()
        observeLiveData()
        onChosen.postValue(true)
    }

    private fun handleClick() {

        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }


        binding.arPhotoButton.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val filename = generateFilename()
        val view: ArSceneView = arFragment.arSceneView

        // Create a bitmap the size of the scene view.
        val bitmap = Bitmap.createBitmap(
            view.width, view.height,
            Bitmap.Config.ARGB_8888
        )

        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()
        // Make the request to copy.
        PixelCopy.request(view, bitmap, { copyResult: Int ->
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    saveBitmapToDisk(bitmap, filename)
                } catch (e: IOException) {
                    val toast = Toast.makeText(
                        this, e.toString(),
                        Toast.LENGTH_LONG
                    )
                    toast.show()
                    return@request
                }
                val snackbar = Snackbar.make(
                    findViewById(android.R.id.content),
                    "Photo saved", Snackbar.LENGTH_LONG
                )
                snackbar.setAction(
                    "Open in Photos"
                ) { v: View? ->
                    val photoFile = File(filename)
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        this.packageName.toString() + ".ar.codelab.name.provider",
                        photoFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW, photoURI)
                    intent.setDataAndType(photoURI, "image/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                }
                snackbar.show()
            } else {
                val toast = Toast.makeText(
                    this,
                    "Failed to copyPixels: $copyResult", Toast.LENGTH_LONG
                )
                toast.show()
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.looper))
    }


    private fun generateFilename(): String {
        val date = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        return Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        ).toString() + File.separator + "Sceneform/" + date + "_screenshot.jpg"
    }

    @Throws(IOException::class)
    private fun saveBitmapToDisk(bitmap: Bitmap, filename: String) {
        val out = File(filename)
        if (!out.parentFile.exists()) {
            out.parentFile.mkdirs()
        }
        try {
            FileOutputStream(filename).use { outputStream ->
                ByteArrayOutputStream().use { outputData ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputData)
                    outputData.writeTo(outputStream)
                    outputStream.flush()
                    outputStream.close()
                }
            }
        } catch (ex: IOException) {
            throw IOException("Failed to save bitmap to disk", ex)
        }
    }

    private fun showSettingsDialog() {
        val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        val inflater: LayoutInflater = this.layoutInflater
        val dialogView: View = inflater.inflate(R.layout.dialog_settings, null)
        dialogBuilder.setView(dialogView)


        alertDialog = dialogBuilder.create()
        alertDialog.setCancelable(false)
        alertDialog.setCanceledOnTouchOutside(false)

        val saveButton = dialogView.findViewById<Button>(R.id.dialog_save)
        val checkBoxDots = dialogView.findViewById<CheckBox>(R.id.checkbox_dots)
        val checkBoxCamera = dialogView.findViewById<CheckBox>(R.id.checkbox_camera)
        val checkBoxDraggable = dialogView.findViewById<CheckBox>(R.id.checkbox_draggable)
        val checkBoxScalable = dialogView.findViewById<CheckBox>(R.id.checkbox_scale)


        checkBoxCamera!!.isChecked = isCameraEnabled.value!!
        checkBoxDots!!.isChecked = isDotsVisible.value!!
        checkBoxDraggable!!.isChecked = isDraggable.value!!
        checkBoxScalable!!.isChecked = isScalable.value!!


        saveButton?.setOnClickListener {
            isDotsVisible.postValue(checkBoxDots.isChecked)
            isCameraEnabled.postValue(checkBoxCamera.isChecked)
            isDraggable.postValue(checkBoxDraggable.isChecked)
            isScalable.postValue(checkBoxScalable.isChecked)

//            galleryModel.frameColor = R.color.red
//            buildModel(galleryModel)
//            setModelOnPlane(hitResult, plane, motionEvent)
            alertDialog.dismiss()

        }


        alertDialog.show()

    }


    private fun handleIntent() {
        val jsonModel = intent.getStringExtra("jsonModel")

        if (!jsonModel.isNullOrBlank()) {

             galleryModel = gson.fromJson(jsonModel, GalleryModel::class.java)

            buildModel(galleryModel)
        }
    }

    private fun observeLiveData() {
        onChosen.observe(this, {
            if (it) {
                initializeAR()
                handleClick()
                binding.settingsButton.visibility = View.VISIBLE
            }
        })



        isCameraEnabled.observe(this, {
            if (it) {
                binding.arPhotoButton.visibility = View.VISIBLE
            } else {
                binding.arPhotoButton.visibility = View.GONE
            }
        })
    }

    private fun arObservables() {

        isDotsVisible.observe(this, {
            arFragment.arSceneView.planeRenderer.isVisible = it
        })

        isScalable.observe(this, {
            if (isExists) {
                artNode.scaleController.isEnabled = it
            }
        })

        isDraggable.observe(this, {
            if (isExists) {
                artNode.translationController.isEnabled = it
            }
        })
    }

    private fun initializeAR() {

        supportFragmentManager.addFragmentOnAttachListener(this)

        if (Sceneform.isSupported(this)) {
            supportFragmentManager.beginTransaction()
                .add(R.id.arFragment, ArFragment::class.java, null)
                .commit()
        }

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

    fun individualDots() {
        val sampler = Texture.Sampler.builder()
            .setMinFilter(Texture.Sampler.MinFilter.LINEAR)
            .setWrapMode(Texture.Sampler.WrapMode.REPEAT)
            .build()

        Texture.builder()
            .setSource(this, R.drawable.red_trigrid)
            .setSampler(sampler)
            .build()
            .thenAccept(Consumer { texture: Texture? ->
                arFragment.arSceneView.planeRenderer
                    .material.thenAccept { material ->
                        material.setTexture(
                            PlaneRenderer.MATERIAL_TEXTURE,
                            texture
                        )
                    }
            })
    }

    private fun buildModel(galleryModel: GalleryModel) {

        val view: View = layoutInflater.inflate(R.layout.layout_art, null)
        val imageView = view.findViewById<View>(R.id.art) as ImageView
        val artFrame = view.findViewById<View>(R.id.art_frame) as RelativeLayout

        Picasso.get()
            .load(galleryModel.imageUrl)
            .into(imageView, object : Callback {
                override fun onSuccess() {

                    view.layoutParams =
                        ViewGroup.LayoutParams(galleryModel.width, galleryModel.height)

                    artFrame.setBackgroundColor(
                        ContextCompat.getColor(
                            this@SceneFormActivity,
                            galleryModel.frameColor
                        )
                    )

                    //artFrame.setBackgroundColor(Color.parseColor("#000000"));


                    ViewRenderable.builder()
                        .setView(this@SceneFormActivity, view)
                        .build()
                        .thenAccept { renderable: ViewRenderable ->
                            artRenderable.postValue(renderable)
                        }
                }

                override fun onError(e: Exception?) {
                    Toast.makeText(
                        this@SceneFormActivity,
                        "Cant build model due to ${e?.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
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
        if (artRenderable.value == null) {
            Toast.makeText(this, "Model is not...", Toast.LENGTH_SHORT).show()
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
            artRenderable.value?.isShadowReceiver = false
            artRenderable.value?.isShadowCaster = false

            artNode = TransformableNode(arFragment.transformationSystem)
            artNode.renderable = artRenderable.value
            anchorNode.addChild(artNode)

            artNode.scaleController.isEnabled = isScalable.value!!
            artNode.translationController.isEnabled = isDraggable.value!!


            //to make the art visible correctly on Vertical wall
            if (plane!!.type == Plane.Type.VERTICAL) {
//                artNode.setLookDirection(Vector3.forward())
                val anchorUp = anchorNode.up
                artNode.setLookDirection(Vector3.up(), anchorUp)
            }

            this.hitResult = hitResult
            this.plane = plane
            this.motionEvent = motionEvent!!
            isExists = true

        } else {
            anchorNode.removeChild(artNode)
            isExists = false

            val widthArray = arrayListOf(150, 200, 300, 400)

            val width = widthArray.random()

//            buildModel(width, width +100)
            setModelOnPlane(hitResult, plane, motionEvent)

        }

    }


    override fun onSessionConfiguration(session: Session?, config: Config?) {
        //3rd
        if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config!!.depthMode = Config.DepthMode.AUTOMATIC
        }

        arObservables()

    }

    override fun onViewCreated(arFragment: ArFragment?, arSceneView: ArSceneView?) {

        // Currently, the tone-mapping should be changed to FILMIC
        // because with other tone-mapping operators except LINEAR
        // the inverseTonemapSRGB function in the materials can produce incorrect results.
        // The LINEAR tone-mapping cannot be used together with the inverseTonemapSRGB function.
        //2nd
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