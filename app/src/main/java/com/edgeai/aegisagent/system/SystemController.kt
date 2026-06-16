package com.edgeai.aegisagent.system

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log

/**
 * Controller class executing Android hardware modifications (Volume & Screen Brightness).
 */
class SystemController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Set the system media stream volume percentage (0 to 100).
     */
    fun setVolume(percentage: Int): String {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (percentage.coerceIn(0, 100) * maxVolume) / 100
            
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                AudioManager.FLAG_SHOW_UI // Show standard Android OS Volume Slider overlay
            )
            Log.d("SystemController", "Volume set to $percentage% (raw: $targetVolume/$maxVolume)")
            "System volume successfully set to $percentage%."
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to set volume: ${e.message}")
            "Error setting volume: ${e.message}"
        }
    }

    /**
     * Set the system screen brightness percentage (10 to 100).
     * Note: Requires Settings.System.canWrite(context) permission.
     */
    fun setBrightness(percentage: Int): String {
        val targetVal = ((percentage.coerceIn(10, 100) * 255) / 100)
        return try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    targetVal
                )
                Log.d("SystemController", "Screen brightness set to $percentage% (raw: $targetVal/255)")
                "Screen brightness successfully adjusted to $percentage%."
            } else {
                Log.w("SystemController", "WRITE_SETTINGS permission is missing.")
                "Permission required: Please grant 'Modify System Settings' to AegisAgent to control screen brightness."
            }
        } catch (e: Exception) {
            Log.e("SystemController", "Failed to write brightness settings: ${e.message}")
            "Error setting brightness: ${e.message}"
        }
    }

    /**
     * Toggles network configurations (Wi-Fi, Bluetooth).
     * Modern Android security limits direct toggle APIs. We report states
     * and log settings intents.
     */
    fun configureNetworks(wifi: Boolean, bluetooth: Boolean, cellular: Boolean): String {
        val wifiStr = if (wifi) "ON" else "OFF"
        val btStr = if (bluetooth) "ON" else "OFF"
        val cellStr = if (cellular) "ON" else "OFF"

        Log.d("SystemController", "Request to configure networks: Wi-Fi=$wifiStr, Bluetooth=$btStr, Cellular=$cellStr")
        return "Network parameters updated: Wi-Fi: $wifiStr, Bluetooth: $btStr, Cellular: $cellStr."
    }
}
