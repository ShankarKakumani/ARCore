package com.shankar.tgp_arcore.util

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.ar.sceneform.ArSceneView
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class PhotoSaver(
    private val activity: Activity
) {

    fun takePhoto(arSceneView: ArSceneView) {
        val bitmap =
            Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)
        val handlerThread = HandlerThread("PixelCopyThread")
        handlerThread.start()

        PixelCopy.request(
            arSceneView,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        saveBitmapToOlderDevice(bitmap)
                    } else {
                        saveBitmapToNewerDevice(bitmap)
                    }
                    activity.runOnUiThread { showSaveSuccessMessage() }
                } else {
                    showSaveErrorMessage()
                }
                handlerThread.quitSafely()
            },
            Handler(handlerThread.looper)
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveBitmapToNewerDevice(bitmap: Bitmap) {
        val uri = generateUri()
        activity.contentResolver.openOutputStream(uri ?: return).use { outputStream ->
            outputStream?.let {
                writeBitmapToJpeg(bitmap, outputStream)
            }
        }
    }

    private fun saveBitmapToOlderDevice(bmp: Bitmap) {
        val filename = generateFilename()
        createDirectory(filename)
        val outputStream = FileOutputStream(filename)

        writeBitmapToJpeg(bmp, outputStream)
        notifyGalleryThatFileHasBeenAdded(filename)
    }

    private fun notifyGalleryThatFileHasBeenAdded(filename: String) {
        // Needed in order for the file to be visible in the gallery, in API versions 28 and before
        MediaScannerConnection.scanFile(activity, arrayOf(filename), null, null)
    }

    private fun generateFilename(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)?.absolutePath +
                "/TryOutFurniture/${getDateFormat()}_screenshot.jpg"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun generateUri(): Uri? {
        val dateFormat = getDateFormat()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${dateFormat}_screenshot.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TryOutFurniture")
        }
        return activity.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    }

    private fun createDirectory(filename: String) {
        val file = File(filename)
        if (doesNotExist(file)) {
            file.parentFile.mkdirs()
        }
    }

    private fun writeBitmapToJpeg(bmp: Bitmap, outputStream: OutputStream) {
        try {
            val outputData = ByteArrayOutputStream()

            bmp.compress(Bitmap.CompressFormat.JPEG, 100, outputData)
            outputData.writeTo(outputStream)

            // Prepare stream to be closed
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            showBitmapWriteErrorMessage()
        }
    }

    private fun doesNotExist(file: File) = !file.parentFile.exists()

    private fun showSaveSuccessMessage() {
        Toast.makeText(activity, "Successfully took photo!", Toast.LENGTH_LONG).show()
    }

    private fun showSaveErrorMessage() {
        Toast.makeText(activity, "Failed to take photo!", Toast.LENGTH_LONG).show()
    }

    private fun showBitmapWriteErrorMessage() =
        Toast.makeText(activity, "Failed to save bitmap to gallery.", Toast.LENGTH_LONG).show()

    private fun getDateFormat(): String {
        return SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
    }
}
