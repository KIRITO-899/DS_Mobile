package com.mobilenetlab.util

import android.content.Context
import com.google.gson.GsonBuilder
import com.mobilenetlab.data.model.BenchmarkMetrics
import com.mobilenetlab.data.model.DeviceInfo
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    data class ExportPayload(
        val timestamp: Long,
        val formattedDate: String,
        val deviceInfo: DeviceInfo,
        val metrics: List<BenchmarkMetrics>
    )

    fun exportToCsv(
        context: Context,
        metrics: List<BenchmarkMetrics>,
        deviceInfo: DeviceInfo
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "mobilenet_benchmark_$timeStamp.csv"
        val exportDir = File(context.getExternalFilesDir(null), "benchmarks")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, fileName)
        FileWriter(file).use { writer ->
            // Metadata header
            writer.append("# MobileNet Lab Benchmark Results\n")
            writer.append("# Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
            writer.append("# Device: ${deviceInfo.manufacturer} ${deviceInfo.model} (API ${deviceInfo.apiLevel})\n")
            writer.append("# CPU Arch: ${deviceInfo.cpuArchitecture}, Cores: ${deviceInfo.numCores}\n")
            writer.append("# RAM: ${deviceInfo.totalRamMB} MB\n")
            writer.append("# Is Emulator: ${deviceInfo.isEmulator}\n")
            writer.append("# ONNX Runtime Version: ${deviceInfo.onnxRuntimeVersion}\n")
            writer.append("\n")

            // CSV Columns
            writer.append("Model Name,Model ID,Model Size (MB),Load Time (ms),Avg Latency (ms),Median Latency (ms),Min Latency (ms),Max Latency (ms),P90 Latency (ms),Throughput (img/s),Peak Memory (MB),Top-1 Accuracy (%),Correct,Incorrect,Total Evaluated,Warmup Iterations,Benchmark Iterations,Provider,Threads,Duration (ms)\n")

            // Rows
            for (m in metrics) {
                writer.append("\"${m.modelName}\",")
                writer.append("${m.modelId},")
                writer.append(String.format(Locale.US, "%.2f,", m.modelSizeMB))
                writer.append(String.format(Locale.US, "%.2f,", m.modelLoadTimeMs))
                writer.append(String.format(Locale.US, "%.2f,", m.avgInferenceMs))
                writer.append(String.format(Locale.US, "%.2f,", m.medianInferenceMs))
                writer.append(String.format(Locale.US, "%.2f,", m.minInferenceMs))
                writer.append(String.format(Locale.US, "%.2f,", m.maxInferenceMs))
                writer.append(String.format(Locale.US, "%.2f,", m.p90InferenceMs))
                writer.append(String.format(Locale.US, "%.2f,", m.throughputImgPerSec))
                writer.append(String.format(Locale.US, "%.2f,", m.peakMemoryMB))
                writer.append(if (m.top1Accuracy >= 0) String.format(Locale.US, "%.2f,", m.top1Accuracy) else "N/A,")
                writer.append("${m.correctPredictions},")
                writer.append("${m.incorrectPredictions},")
                writer.append("${m.totalEvaluated},")
                writer.append("${m.warmupIterations},")
                writer.append("${m.benchmarkIterations},")
                writer.append("${m.executionProvider},")
                writer.append("${m.numThreads},")
                writer.append("${m.totalBenchmarkDurationMs}\n")
            }
        }
        return file
    }

    fun exportToJson(
        context: Context,
        metrics: List<BenchmarkMetrics>,
        deviceInfo: DeviceInfo
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "mobilenet_benchmark_$timeStamp.json"
        val exportDir = File(context.getExternalFilesDir(null), "benchmarks")
        if (!exportDir.exists()) exportDir.mkdirs()

        val payload = ExportPayload(
            timestamp = System.currentTimeMillis(),
            formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            deviceInfo = deviceInfo,
            metrics = metrics
        )

        val gson = GsonBuilder().setPrettyPrinting().create()
        val file = File(exportDir, fileName)
        file.writeText(gson.toJson(payload))
        return file
    }
}
