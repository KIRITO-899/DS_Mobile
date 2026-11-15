package com.mobilenetlab.data.model

/**
 * Represents the measured performance metrics for a single model benchmark run.
 * All latency values are in milliseconds. Memory values are in MB.
 */
data class BenchmarkMetrics(
    val modelId: String,
    val modelName: String,
    val modelSizeMB: Double,
    val modelLoadTimeMs: Double,

    // Inference latency metrics (pure inference, excluding preprocessing)
    val avgInferenceMs: Double,
    val medianInferenceMs: Double,
    val minInferenceMs: Double,
    val maxInferenceMs: Double,
    val p90InferenceMs: Double,
    val allInferenceTimesMs: List<Double>,

    // Preprocessing latency
    val avgPreprocessMs: Double,

    // End-to-end latency (preprocess + inference)
    val avgEndToEndMs: Double,

    // Throughput
    val throughputImgPerSec: Double,

    // Memory
    val peakMemoryMB: Double,

    // Accuracy (if test dataset was used)
    val top1Accuracy: Double = -1.0,
    val top5Accuracy: Double = -1.0,
    val correctPredictions: Int = 0,
    val incorrectPredictions: Int = 0,
    val totalEvaluated: Int = 0,

    // Configuration
    val warmupIterations: Int,
    val benchmarkIterations: Int,
    val executionProvider: String,
    val numThreads: Int,
    val totalBenchmarkDurationMs: Long,

    // Warnings/errors
    val warnings: List<String> = emptyList()
)
