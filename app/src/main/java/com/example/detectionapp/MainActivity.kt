package com.example.edgedetection

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.edgedetection.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var glRenderer: GLView
    private lateinit var cameraController: CameraController

    private val CAMERA_PERMISSION_REQUEST_CODE = 101

    // --- Native Methods ---
    private external fun initNative()
    private external fun destroyNative()
    private external fun processFrameNative(
        width: Int,
        height: Int,
        yPlane: java.nio.ByteBuffer,
        yStride: Int
    )
    // --- End Native Methods ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fpsText.text = "FPS: ..."

        // --- Setup GL View ---
        glSurfaceView = binding.glSurfaceView
        glRenderer = GLView()

        // Set the FPS listener
        glRenderer.fpsListener = { fps ->
            // This listener is called from the GL thread.
            // We must use runOnUiThread to update the UI.
            runOnUiThread {
                binding.fpsText.text = String.format("FPS: %.1f", fps)
            }
        }

        // Configure the GLSurfaceView
        glSurfaceView.setEGLContextClientVersion(2) // We want OpenGL ES 2.0
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY // For live video

        // --- Permission Check & Camera Setup ---
        if (checkCameraPermission()) {
            setupCamera()
            initNative() // Init the C++ processor
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCamera()
                initNative() // Init C++ processor
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupCamera() {
        // Pass the JNI function as a lambda
        // We pass 'null' for the TextureView because we are rendering to GLSurfaceView
        cameraController = CameraController(
            this,
            null, // <-- No TextureView preview
            { width, height, buffer, stride ->
                processFrameNative(width, height, buffer, stride)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (::cameraController.isInitialized) {
            cameraController.start()
        }
    }

    override fun onPause() {
        if (::cameraController.isInitialized) {
            cameraController.stop()
        }
        glSurfaceView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyNative()
        super.onDestroy()
    }

    companion object {
        // Load the 'native-lib' library
        init {
            System.loadLibrary("native-lib")
        }
    }
}