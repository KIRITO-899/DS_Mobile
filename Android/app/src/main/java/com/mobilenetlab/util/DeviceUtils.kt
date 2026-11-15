package com.mobilenetlab.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.mobilenetlab.data.model.DeviceInfo
import java.io.File

object DeviceUtils {

    fun getDeviceInfo(context: Context, onnxRuntimeVersion: String = "1.17.0"): DeviceInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalRamMB = memInfo.totalMem / (1024 * 1024)
        val availRamMB = memInfo.availMem / (1024 * 1024)

        val isEmulator = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")

        val processorInfo = try {
            val cpuInfo = File("/proc/cpuinfo")
            if (cpuInfo.exists()) {
                cpuInfo.readLines().firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }
                    ?.substringAfter(":")?.trim() ?: Build.HARDWARE
            } else {
                Build.HARDWARE
            }
        } catch (e: Exception) {
            Build.HARDWARE
        }

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            cpuArchitecture = System.getProperty("os.arch") ?: "unknown",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamMB = totalRamMB,
            availableRamMB = availRamMB,
            isEmulator = isEmulator,
            onnxRuntimeVersion = onnxRuntimeVersion,
            processorInfo = processorInfo,
            numCores = Runtime.getRuntime().availableProcessors()
        )
    }
}
