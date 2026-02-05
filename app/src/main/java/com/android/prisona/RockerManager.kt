package com.android.prisona

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.android.prison.entity.PLocation
import com.android.prison.manager.PLocationManager
import com.android.prisona.widget.EnFloatView
import com.imuxuan.floatingview.FloatingMagnetView
import com.imuxuan.floatingview.FloatingView
import kotlin.math.cos
import kotlin.math.sin
import com.android.prison.utils.Logger

object RockerManager {

    private const val TAG = "RockerManager"
    private var isInitialized = false

    // Earth radius constants for coordinate calculations
    private const val Ea = 6378137.0     // Equator radius (meters)
    private const val Eb = 6356725.0     // Polar radius (meters)

    fun init(application: Application?, userId: Int) {
        try {
            if (isInitialized) {
                Logger.d(TAG, "RockerManager already initialized, skipping...")
                return
            }

            if (application == null) {
                Logger.w(TAG, "Application is null, cannot initialize RockerManager")
                return
            }

            // Check if required permissions are granted
            if (!checkPermissions(application)) {
                Logger.w(TAG, "Required permissions not granted, RockerManager cannot initialize")
                Logger.w(TAG, "Please grant: ${getRequiredPermissions().joinToString(", ")}")
                return
            }

            if (!PLocationManager.isFakeLocationEnable()) {
                Logger.d(TAG, "Fake location is not enabled, RockerManager will not initialize")
                return
            }

            Logger.d(TAG, "Initializing RockerManager for userId: $userId")

            val enFloatView = initFloatView()
            if (enFloatView is EnFloatView) {
                enFloatView.setListener { angle: Float, distance: Float ->
                    changeLocation(distance, angle, application.packageName, userId)
                }
                Logger.d(TAG, "Floating view initialized successfully")
            } else {
                Logger.w(TAG, "Failed to initialize floating view")
                return
            }

            // Register activity lifecycle callbacks for floating view management
            application.registerActivityLifecycleCallbacks(object : BaseActivityLifecycleCallback {
                override fun onActivityStarted(activity: Activity) {
                    super.onActivityStarted(activity)
                    try {
                        FloatingView.get().attach(activity)
                        Logger.d(TAG, "Floating view attached to activity: ${activity.javaClass.simpleName}")
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error attaching floating view to activity: ${e.message}")
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    super.onActivityStopped(activity)
                    try {
                        FloatingView.get().detach(activity)
                        Logger.d(TAG, "Floating view detached from activity: ${activity.javaClass.simpleName}")
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error detaching floating view from activity: ${e.message}")
                    }
                }
            })

            isInitialized = true
            Logger.d(TAG, "RockerManager initialized successfully - Floating GPS joystick is now active!")

        } catch (e: Exception) {
            Logger.e(TAG, "Error initializing RockerManager: ${e.message}")
            Logger.e(TAG, "Stack trace: ", e)
        }
    }

    private fun initFloatView(): FloatingMagnetView? {
        return try {
            val params = FrameLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )

            params.gravity = Gravity.START or Gravity.CENTER
            val view = EnFloatView(FoxRiver.getContext())
            view.layoutParams = params

            FloatingView.get().customView(view)
            Logger.d(TAG, "Floating view created successfully")

            FloatingView.get().view
        } catch (e: Exception) {
            Logger.e(TAG, "Error creating floating view: ${e.message}")
            null
        }
    }

    private fun changeLocation(distance: Float, angle: Float, packageName: String, userId: Int) {
        try {
            val location = PLocationManager.get().getLocation(userId, packageName)
            if (location == null) {
                Logger.w(TAG, "No current location found for package: $packageName, userId: $userId")
                return
            }

            Logger.d(TAG, "Changing location - Distance: ${distance}m, Angle: ${angle}°, Current: ${location.latitude}, ${location.longitude}")

            // Calculate new coordinates based on joystick input
            val dx = distance * sin(angle * Math.PI / 180.0)
            val dy = distance * cos(angle * Math.PI / 180.0)

            // Use ellipsoid model for more accurate coordinate calculations
            val ec = Eb + (Ea - Eb) * (90.0 - location.latitude) / 90.0
            val ed = ec * cos(location.latitude * Math.PI / 180)

            val newLng = (dx / ed + location.longitude * Math.PI / 180.0) * 180.0 / Math.PI
            val newLat = (dy / ec + location.latitude * Math.PI / 180.0) * 180.0 / Math.PI

            val newLocation = PLocation(newLat, newLng)

            // Update the location
            PLocationManager.get().setLocation(userId, packageName, newLocation)

            Logger.d(TAG, "Location updated - New: ${newLat}, ${newLng}")

        } catch (e: Exception) {
            Logger.e(TAG, "Error changing location: ${e.message}")
            Logger.e(TAG, "Stack trace: ", e)
        }
    }

    /**
     * Check if RockerManager is currently initialized and active
     */
    fun isActive(): Boolean {
        return isInitialized
    }

    /**
     * Check if required permissions are granted for RockerManager to work
     */
    fun checkPermissions(context: Context): Boolean {
        return try {
            // Check if overlay permission is granted (required for floating view)
            val hasOverlayPermission = Settings.canDrawOverlays(context)
            if (!hasOverlayPermission) {
                Logger.w(TAG, "Overlay permission not granted - RockerManager cannot show floating view")
                return false
            }

            // Check if location permissions are granted
            val hasLocationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasLocationPermission) {
                Logger.w(TAG, "Location permission not granted - RockerManager cannot access location")
                return false
            }

            Logger.d(TAG, "All required permissions are granted")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking permissions: ${e.message}")
            false
        }
    }

    /**
     * Get a list of required permissions for RockerManager
     */
    fun getRequiredPermissions(): List<String> {
        return listOf(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * Clean up resources (useful for testing or when disabling the feature)
     */
    fun cleanup() {
        try {
            isInitialized = false
            Logger.d(TAG, "RockerManager cleaned up")
        } catch (e: Exception) {
            Logger.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}