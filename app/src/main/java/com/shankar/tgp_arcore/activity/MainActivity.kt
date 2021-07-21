package com.shankar.tgp_arcore.activity

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.core.ArCoreApk.InstallStatus
import com.google.ar.core.exceptions.*
import com.shankar.tgp_arcore.databinding.ActivityMainBinding
import com.shankar.tgp_arcore.helpers.*
import com.shankar.tgp_arcore.samplerender.*
import com.shankar.tgp_arcore.samplerender.arcore.BackgroundRenderer
import com.shankar.tgp_arcore.samplerender.arcore.PlaneRenderer
import com.shankar.tgp_arcore.samplerender.arcore.SpecularCubemapFilter
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity(), SampleRender.Renderer {

    private lateinit var binding : ActivityMainBinding
    private lateinit var surfaceView : GLSurfaceView
    private var session: Session? = null

    private lateinit var planeRenderer: PlaneRenderer
    private lateinit var backgroundRenderer: BackgroundRenderer
    private lateinit var virtualSceneFramebuffer : Framebuffer

    private lateinit var tapHelper: TapHelper
    private val anchors = ArrayList<Anchor>()

    private val messageSnackBarHelper: SnackbarHelper = SnackbarHelper()
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private val trackingStateHelper = TrackingStateHelper(this)

    // Point Cloud
    private lateinit var pointCloudVertexBuffer: VertexBuffer
    private lateinit var pointCloudMesh: Mesh
    private lateinit var pointCloudShader: Shader

    // Environmental HDR
    private lateinit var dfgTexture: Texture
    private lateinit var cubemapFilter: SpecularCubemapFilter

    private lateinit var virtualObjectMesh: Mesh
    private lateinit var virtualObjectShader: Shader

    private val SEARCHING_PLANE_MESSAGE = "Searching for surfaces..."
    private val WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object."
    private val TAG: String = MainActivity::class.java.simpleName
    private var installRequested = false
    private var hasSetTextureNames = false

    private val Z_NEAR = 0.1f
    private val Z_FAR = 100f
    private val CUBEMAP_RESOLUTION = 16
    private val CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32
    private var lastPointCloudTimestamp: Long = 0


    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16) // view x model
    private val modelViewProjectionMatrix = FloatArray(16) // projection x view x model
    private val sphericalHarmonicsCoefficients = FloatArray(9 * 3)
    private val viewInverseMatrix = FloatArray(16)
    private val worldLightDirection = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
    private val viewLightDirection = FloatArray(4) // view x world light direction
    private val APPROXIMATE_DISTANCE_METERS = 2.0f

    private val sphericalHarmonicFactors = floatArrayOf(
        0.282095f,
        -0.325735f,
        0.325735f,
        -0.325735f,
        0.273137f,
        -0.273137f,
        0.078848f,
        -0.273137f,
        0.136569f
    )


    private val depthSettings = DepthSettings()
    private val depthSettingsMenuDialogCheckboxes = BooleanArray(2)

    private val instantPlacementSettings = InstantPlacementSettings()
    private val instantPlacementSettingsMenuDialogCheckboxes = BooleanArray(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        surfaceView = binding.surfaceView
        displayRotationHelper = DisplayRotationHelper(this)
        tapHelper = TapHelper(this)
        surfaceView.setOnTouchListener(tapHelper)


        // Set up renderer.
        val render = SampleRender(surfaceView, this, assets)

    }


    override fun onSurfaceCreated(render: SampleRender?) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {
            planeRenderer = PlaneRenderer(render)
            backgroundRenderer = BackgroundRenderer(render)
            virtualSceneFramebuffer = Framebuffer(render,  /*width=*/1,  /*height=*/1)
            cubemapFilter = SpecularCubemapFilter(
                render,
                CUBEMAP_RESOLUTION,
                CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES
            )
            // Load DFG lookup table for environmental lighting
            dfgTexture = Texture(
                render,
                Texture.Target.TEXTURE_2D,
                Texture.WrapMode.CLAMP_TO_EDGE,  /*useMipmaps=*/
                false
            )
            // The dfg.raw file is a raw half-float texture with two channels.
            val dfgResolution = 64
            val dfgChannels = 2
            val halfFloatSize = 2
            val buffer =
                ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize)
            assets.open("models/dfg.raw").use { `is` -> `is`.read(buffer.array()) }
            // SampleRender abstraction leaks here.
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.textureId)
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture")
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,  /*level=*/
                0,
                GLES30.GL_RG16F,  /*width=*/
                dfgResolution,  /*height=*/
                dfgResolution,  /*border=*/
                0,
                GLES30.GL_RG,
                GLES30.GL_HALF_FLOAT,
                buffer
            )
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D")

            // Point cloud
            pointCloudShader = Shader.createFromAssets(
                render, "shaders/point_cloud.vert", "shaders/point_cloud.frag",  /*defines=*/null
            )
                .setVec4(
                    "u_Color", floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f)
                )
                .setFloat("u_PointSize", 5.0f)
            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                VertexBuffer(render,  /*numberOfEntriesPerVertex=*/4,  /*entries=*/null)
            val pointCloudVertexBuffers = arrayOf(pointCloudVertexBuffer)
            pointCloudMesh = Mesh(
                render, Mesh.PrimitiveMode.POINTS,  /*indexBuffer=*/null, pointCloudVertexBuffers
            )

            // Virtual object to render (ARCore pawn)
            val virtualObjectAlbedoTexture = Texture.createFromAsset(
                render,
                "models/pawn_albedo.png",
                Texture.WrapMode.CLAMP_TO_EDGE,
                Texture.ColorFormat.SRGB
            )
            val virtualObjectPbrTexture = Texture.createFromAsset(
                render,
                "models/pawn_roughness_metallic_ao.png",
                Texture.WrapMode.CLAMP_TO_EDGE,
                Texture.ColorFormat.LINEAR
            )
            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj")
            virtualObjectShader = Shader.createFromAssets(
                render,
                "shaders/environmental_hdr.vert",
                "shaders/environmental_hdr.frag",  /*defines=*/
                object : HashMap<String?, String?>() {
                    init {
                        put(
                            "NUMBER_OF_MIPMAP_LEVELS",
                            Integer.toString(cubemapFilter.numberOfMipmapLevels)
                        )
                    }
                })
                .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
                .setTexture("u_Cubemap", cubemapFilter.filteredCubemapTexture)
                .setTexture("u_DfgTexture", dfgTexture)
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Failed to read a required asset file",
                e
            )
            messageSnackBarHelper.showError(this, "Failed to read a required asset file: $e")
        }
    }

    override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        virtualSceneFramebuffer.resize(width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        if (session == null) {
            return
        }

        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session!!.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
            hasSetTextureNames = true
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session)

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        val frame: Frame = try {
            session!!.update()
        } catch (e: CameraNotAvailableException) {
            Log.e(
                TAG,
                "Camera not available during onDrawFrame",
                e
            )
            messageSnackBarHelper.showError(this, "Camera not available. Try restarting the app.")
            return
        }
        val camera = frame.camera

        // Update BackgroundRenderer state to match the depth settings.
        try {
            backgroundRenderer.setUseDepthVisualization(
                render, depthSettings.depthColorVisualizationEnabled()
            )
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion())
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Failed to read a required asset file",
                e
            )
            messageSnackBarHelper.showError(this, "Failed to read a required asset file: $e")
            return
        }
        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame)
        if (camera.trackingState == TrackingState.TRACKING
            && (depthSettings.useDepthForOcclusion()
                    || depthSettings.depthColorVisualizationEnabled())
        ) {
            try {
                val depthImage =frame.acquireDepthImage()
                backgroundRenderer.updateCameraDepthTexture(depthImage)


            } catch (e: NotYetAvailableException) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
        }

        // Handle one tap per frame.
        handleTap(frame, camera)

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

        // Show a message based on whether tracking has failed, if planes are detected, and if the user
        // has placed any objects.
        var message: String? = null
        if (camera.trackingState == TrackingState.PAUSED) {
            message = if (camera.trackingFailureReason == TrackingFailureReason.NONE) {
                SEARCHING_PLANE_MESSAGE
            } else {
                TrackingStateHelper.getTrackingFailureReasonString(camera)
            }
        } else if (hasTrackingPlane()) {
            if (anchors.isEmpty()) {
                message =
                    WAITING_FOR_TAP_MESSAGE
            }
        } else {
            message =
                SEARCHING_PLANE_MESSAGE
        }
        if (message == null) {
            messageSnackBarHelper.hide(this)
        } else {
            messageSnackBarHelper.showMessage(this, message)
        }

        // -- Draw background
        if (frame.timestamp != 0L) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render)
        }

        // If not tracking, don't draw 3D objects.
        if (camera.trackingState == TrackingState.PAUSED) {
            return
        }

        // -- Draw non-occluded virtual objects (planes, point cloud)

        // Get projection matrix.
        camera.getProjectionMatrix(
            projectionMatrix,
            0,
            Z_NEAR,
            Z_FAR
        )

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0)
//        frame.acquirePointCloud().use { pointCloud ->
//            if (pointCloud.timestamp > lastPointCloudTimestamp) {
//                pointCloudVertexBuffer.set(pointCloud.points)
//                lastPointCloudTimestamp = pointCloud.timestamp
//            }
//            Matrix.multiplyMM(
//                modelViewProjectionMatrix,
//                0,
//                projectionMatrix,
//                0,
//                viewMatrix,
//                0
//            )
//            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
//            render.draw(pointCloudMesh, pointCloudShader)
//        }

        // Visualize planes.
        planeRenderer.drawPlanes(
            render,
            session!!.getAllTrackables(Plane::class.java),
            camera.displayOrientedPose,
            projectionMatrix
        )

        // -- Draw occluded virtual objects

        // Update lighting parameters in the shader
        updateLightEstimation(frame.lightEstimate, viewMatrix)

        // Visualize anchors created by touch.
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
        for (anchor in anchors) {
            if (anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to session.update() as ARCore refines its estimate of the world.
            anchor.pose.toMatrix(modelMatrix, 0)

            // Calculate model/view/projection matrices
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            // Update shader properties and draw
            virtualObjectShader.setMat4("u_ModelView", modelViewMatrix)
            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            //            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
            render.draw(virtualObjectMesh, virtualObjectShader)
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(
            render,
            virtualSceneFramebuffer,
            Z_NEAR,
            Z_FAR
        )
    }

    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = tapHelper.poll()
        if (tap != null && camera.trackingState == TrackingState.TRACKING) {
            val hitResultList: List<HitResult> =
                if (instantPlacementSettings.isInstantPlacementEnabled) {
                    frame.hitTestInstantPlacement(
                        tap.x,
                        tap.y,
                        APPROXIMATE_DISTANCE_METERS
                    )
                } else {
                    frame.hitTest(tap)
                }
            for (hit in hitResultList) {
                // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor.
                val trackable = hit.trackable
                // If a plane was hit, check that it was hit inside the plane polygon.
                // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC.
                if ((trackable is Plane
                            && trackable.isPoseInPolygon(hit.hitPose)
                            && PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0)
                    || (trackable is Point
                            && trackable.orientationMode
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                    || trackable is InstantPlacementPoint
                    || trackable is DepthPoint
                ) {
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size >= 20) {
                        anchors[0].detach()
                        anchors.removeAt(0)
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(hit.createAnchor())
                    // For devices that support the Depth API, shows a dialog to suggest enabling
                    // depth-based occlusion. This dialog needs to be spawned on the UI thread.

                    // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                    // Instant Placement Point.
                    break
                }
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(
                this,
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            )
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    private fun updateLightEstimation(lightEstimate: LightEstimate, viewMatrix: FloatArray) {
        if (lightEstimate.state != LightEstimate.State.VALID) {
            virtualObjectShader.setBool("u_LightEstimateIsValid", false)
            return
        }
        virtualObjectShader.setBool("u_LightEstimateIsValid", true)
        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0)
        virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix)
        updateMainLight(
            lightEstimate.environmentalHdrMainLightDirection,
            lightEstimate.environmentalHdrMainLightIntensity,
            viewMatrix
        )
        updateSphericalHarmonicsCoefficients(
            lightEstimate.environmentalHdrAmbientSphericalHarmonics
        )
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap())
    }
    private fun updateMainLight(
        direction: FloatArray,
        intensity: FloatArray,
        viewMatrix: FloatArray
    ) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0]
        worldLightDirection[1] = direction[1]
        worldLightDirection[2] = direction[2]
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0)
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection)
        virtualObjectShader.setVec3("u_LightIntensity", intensity)
    }

    private fun updateSphericalHarmonicsCoefficients(coefficients: FloatArray) {
        // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
        // constants in sphericalHarmonicFactors were derived from three terms:
        //
        // 1. The normalized spherical harmonics basis functions (y_lm)
        //
        // 2. The lambertian diffuse BRDF factor (1/pi)
        //
        // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
        // of all incoming light over a hemisphere for a given surface normal, which is what the shader
        // (environmental_hdr.frag) expects.
        //
        // You can read more details about the math here:
        // https://google.github.io/filament/Filament.html#annex/sphericalharmonics
        require(coefficients.size == 9 * 3) { "The given coefficients array must be of length 27 (3 components per 9 coefficients" }

        // Apply each factor to every component of each coefficient
        for (i in 0 until 9 * 3) {
            sphericalHarmonicsCoefficients[i] =
                coefficients[i] * sphericalHarmonicFactors.get(
                    i / 3
                )
        }
        virtualObjectShader.setVec3Array(
            "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients
        )
    }


    override fun onResume() {

        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    InstallStatus.INSTALLED -> {
                    }
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this)
                    return
                }

                // Create the session.
                session = Session( /* context= */this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: UnavailableDeviceNotCompatibleException) {
                message = "This device does not support AR"
                exception = e
            } catch (e: Exception) {
                message = "Failed to create AR session"
                exception = e
            }
            if (message != null) {
                messageSnackBarHelper.showError(this, message)
                Log.e(
                    TAG,
                    "Exception creating session",
                    exception
                )
                return
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession()
            // To record a live camera session for later playback, call
            // `session.startRecording(recorderConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDataset(playbackDatasetPath)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            messageSnackBarHelper.showError(this, "Camera not available. Try restarting the app.")
            session = null
            return
        }

        surfaceView.onResume()
        displayRotationHelper.onResume()

        super.onResume()

    }


    private fun hasTrackingPlane(): Boolean {
        for (plane in session!!.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState == TrackingState.TRACKING) {
                return true
            }
        }
        return false
    }

    override fun onPause() {
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call session.update() and get a SessionPausedException.
        session.let {
            displayRotationHelper.onPause()
            surfaceView.onPause()
            session?.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {

        session.let {
            session?.close()
            session = null
        }
        super.onDestroy()
    }

    private fun configureSession() {
        val config = session!!.config
        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        } else {
            config.depthMode = Config.DepthMode.DISABLED
        }
        if (instantPlacementSettings.isInstantPlacementEnabled) {
            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
        } else {
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        }
        session!!.configure(config)
    }

}