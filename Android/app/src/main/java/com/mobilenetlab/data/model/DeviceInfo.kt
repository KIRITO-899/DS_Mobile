package com.mobilenetlab.data.model

/**
 * Holds device information captured during a benchmark session.
 */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val apiLevel: Int,
    val cpuArchitecture: String,
    val supportedAbis: List<String>,
    val totalRamMB: Long,
    val availableRamMB: Long,
    val isEmulator: Boolean,
    val onnxRuntimeVersion: String,
    val processorInfo: String,
    val numCores: Int
)
