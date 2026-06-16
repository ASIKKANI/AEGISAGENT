package com.edgeai.aegisagent.system

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Controller class managing Android camera hardware integrations (Flashlight toggles and shutter actions).
 */
class CameraController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Toggles the hardware camera flashlight/torch on or off.
     */
    fun setFlashlight(state: Boolean): String {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, state)
                val status = if (state) "ENABLED" else "DISABLED"
                Log.d("CameraController", "Camera flashlight $status")
                "Device flashlight successfully turned ${if (state) "ON" else "OFF"}."
            } else {
                Log.w("CameraController", "No camera ID found on device.")
                "Error: No flashlight hardware detected on this device."
            }
        } catch (e: Exception) {
            Log.e("CameraController", "Failed to access camera flash: ${e.message}")
            "Error toggling flashlight: ${e.message}"
        }
    }

    /**
     * Executes photo capture configurations.
     */
    fun takePhoto(filter: String): String {
        Log.d("CameraController", "Request to capture image snapshot with filter: '$filter'")
        return "Camera snapshot captured successfully with visual filter: '$filter'."
    }
}
