package com.shankar.tgp_arcore.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import androidx.lifecycle.MutableLiveData
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
import com.google.ar.sceneform.ux.TransformableNode
import com.google.gson.Gson
import com.jraska.falcon.Falcon
import com.shankar.tgp_arcore.R
import com.shankar.tgp_arcore.data.GalleryModel
import com.shankar.tgp_arcore.databinding.ActivitySceneFormBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
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
    private lateinit var artTransformableNode: TransformableNode
    private lateinit var artNode: Node

    private lateinit var hitResult: HitResult
    private lateinit var plane: Plane
    private lateinit var motionEvent: MotionEvent

    private lateinit var galleryModel: GalleryModel

    private val onChosen = MutableLiveData(false)
    private var isExists = false
    private val isDotsVisible = MutableLiveData(true)
    private val isCameraEnabled = MutableLiveData(true)
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
//            val bitmap = takeScreenshot()
//            saveBitmap(bitmap!!)

            val screenshotFile = getScreenshotFile()

            Falcon.takeScreenshot(this, screenshotFile)

            val bitmap: Bitmap = Falcon.takeScreenshotBitmap(this)

            val message = "Screenshot captured to " + screenshotFile!!.absolutePath
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            val uri: Uri = Uri.fromFile(screenshotFile)
            val scanFileIntent = Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri
            )
            sendBroadcast(scanFileIntent)
        }
    }

    protected open fun getScreenshotFile(): File? {
        val screenshotDirectory: File?
        try {
            screenshotDirectory = getScreenshotsDirectory(applicationContext)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
        val dateFormat: DateFormat =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS", Locale.getDefault())
        val screenshotName: String = dateFormat.format(Date()).toString() + ".png"
        return File(screenshotDirectory, screenshotName)
    }

    @Throws(IllegalAccessException::class)
    open fun getScreenshotsDirectory(context: Context): File? {
        val dirName = "screenshots_" + context.getPackageName()
        val rootDir: File? =
            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            } else {
                context.getDir("screens", MODE_PRIVATE)
            }

        val directory = File(rootDir, dirName)
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw IllegalAccessException("Unable to create screenshot directory " + directory.absolutePath)
            }
        }
        return directory
    }

    open fun takeScreenshot(): Bitmap? {
        val rootView = findViewById<View>(android.R.id.content).rootView
        rootView.isDrawingCacheEnabled = true
        return rootView.drawingCache
    }

    open fun saveBitmap(bitmap: Bitmap) {
        val imagePath =
            File(Environment.getExternalStorageDirectory().toString() + "/screenshot.png")
        val fos: FileOutputStream
        try {
            fos = FileOutputStream(imagePath)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
        } catch (e: FileNotFoundException) {
            Log.e("GREC", e.message, e)
        } catch (e: IOException) {
            Log.e("GREC", e.message, e)
        }
    }    // for api level 28
    private fun getScreenShotFromView(view: View, activity: Activity, callback: (Bitmap) -> Unit) {
        activity.window?.let { window ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val locationOfViewInWindow = IntArray(2)
            view.getLocationInWindow(locationOfViewInWindow)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PixelCopy.request(
                        window,
                        Rect(
                            locationOfViewInWindow[0],
                            locationOfViewInWindow[1],
                            locationOfViewInWindow[0] + view.width,
                            locationOfViewInWindow[1] + view.height
                        ), bitmap, { copyResult ->
                            if (copyResult == PixelCopy.SUCCESS) {
                                callback(bitmap)
                            } else {

                            }
                            // possible to handle other result codes ...
                        },
                        Handler()
                    )
                }
            } catch (e: IllegalArgumentException) {
                // PixelCopy may throw IllegalArgumentException, make sure to handle it
                e.printStackTrace()
            }
        }
    }

//    private fun takePhoto() {
//        val bitmap = Bitmap.createBitmap(
//            this.arFragment.arSceneView.width,
//            this.arFragment.arSceneView.height,
//            Bitmap.Config.ARGB_8888
//        )
//        PixelCopy.request(
//            this.arFragment.arSceneView, bitmap, { result ->
//                when (result) {
//                    PixelCopy.SUCCESS -> {
//                        applicationContext?.createExternalFile(
//                            environment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                                Environment.DIRECTORY_SCREENSHOTS
//                            } else {
//                                Environment.DIRECTORY_PICTURES
//                            }, extension = ".png"
//                        )?.let { file ->
//                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
//                            Intents.view(this, file, "image/png")
//                        }
//                    }
//                    else -> showToast("Screenshot failure: $result")
//                }
//            }, Handler(
//                HandlerThread("screenshot")
//                    .apply { start() }.looper
//            )
//        )
//    }

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
                artTransformableNode.scaleController.isEnabled = it
            }
        })

        isDraggable.observe(this, {
            if (isExists) {
                artTransformableNode.translationController.isEnabled = it
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
            Toast.makeText(this, "Model is not build yet...", Toast.LENGTH_SHORT).show()
            buildModel(galleryModel)
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

            artNode = Node()
            artNode.renderable = artRenderable.value

            artTransformableNode = TransformableNode(arFragment.transformationSystem)
            artTransformableNode.renderable = artRenderable.value

            anchorNode.addChild(artNode)

            artTransformableNode.scaleController.isEnabled = isScalable.value!!
            artTransformableNode.translationController.isEnabled = isDraggable.value!!

            //to make the art visible correctly on Vertical wall
            if (plane!!.type == Plane.Type.VERTICAL) {
//                anchorNode.setLookDirection(Vector3.forward())
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