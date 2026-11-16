package com.mobilenetlab.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.mobilenetlab.data.model.BenchmarkMetrics
import com.mobilenetlab.data.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Standardized benchmarking engine.
 * Ensures strictly fair and reproducible evaluation:
 * - Sequential execution across models
 * - Identical test images in identical order
 * - Pure inference latency separated from preprocessing
 * - Warmup phase discarded before measurement
 * - Statistical aggregation: mean, median, min, max, P90, throughput
 * - Memory tracking before/after
 */
enum class DeviceHardwareTier(
    val displayName: String,
    val scaleFactor: Double,
    val jitterFactor: Double,
    val ramScale: Double
) {
    LIVE("Live Device (Real Hardware)", 1.0, 1.0, 1.0),
    HIGH_END("High-End Flagship (Simulated)", 0.35, 0.4, 2.5),
    MID_RANGE("Mid-Range Phone (Simulated)", 1.0, 1.0, 1.0),
    LOW_END("Low-End Budget (Simulated)", 2.45, 2.2, 0.5)
}

class BenchmarkEngine(
    private val context: Context,
    private val inferenceEngine: OnnxInferenceEngine
) {

    data class BenchmarkConfig(
        val warmupIterations: Int = 10,
        val benchmarkIterations: Int = 100,
        val useNNAPI: Boolean = false,
        val numThreads: Int = 4,
        val modelsToBenchmark: List<ModelConfig> = ModelConfig.ALL_MODELS,
        val deviceTier: DeviceHardwareTier = DeviceHardwareTier.LIVE
    )

    data class BenchmarkProgress(
        val currentModelIndex: Int,
        val totalModels: Int,
        val currentModelName: String,
        val currentIteration: Int,
        val totalIterations: Int,
        val phase: Phase,
        val progressPercent: Int
    )

    enum class Phase {
        LOADING,
        WARMUP,
        BENCHMARKING,
        COMPLETED,
        ERROR
    }

    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    /**
     * Run full benchmarking suite across selected models.
     */
    suspend fun runBenchmark(
        config: BenchmarkConfig,
        testBitmaps: List<Bitmap>,
        testLabels: List<Int>? = null,
        onProgress: (BenchmarkProgress) -> Unit
    ): List<BenchmarkMetrics> = withContext(Dispatchers.Default) {
        isCancelled = false
        val results = mutableListOf<BenchmarkMetrics>()

        val totalModels = config.modelsToBenchmark.size

        for ((index, modelConfig) in config.modelsToBenchmark.withIndex()) {
            if (isCancelled) break

            // Verify model availability
            if (!inferenceEngine.isModelAvailable(modelConfig)) {
                results.add(
                    createUnavailableMetrics(modelConfig, config, "Model file not found in assets")
                )
                continue
            }

            try {
                // Phase 1: Loading
                onProgress(
                    BenchmarkProgress(
                        currentModelIndex = index,
                        totalModels = totalModels,
                        currentModelName = modelConfig.displayName,
                        currentIteration = 0,
                        totalIterations = config.benchmarkIterations,
                        phase = Phase.LOADING,
                        progressPercent = (index * 100) / totalModels
                    )
                )

                // Force GC before loading
                System.gc()
                delay(100)

                val initialMem = getMemoryUsageMB()
                val loadTimeMs = inferenceEngine.loadModel(
                    modelConfig,
                    useNNAPI = config.useNNAPI,
                    numThreads = config.numThreads
                )

                val modelFileSizeBytes = inferenceEngine.getModelFileSize(modelConfig)
                val modelSizeMB = if (modelFileSizeBytes > 0) {
                    modelFileSizeBytes / (1024.0 * 1024.0)
                } else 0.0

                // Prepare test buffers
                val numImages = minOf(testBitmaps.size, config.benchmarkIterations)
                val preprocessedBuffers = ArrayList<FloatBuffer>(maxOf(1, numImages))
                val preprocessTimes = ArrayList<Double>(maxOf(1, numImages))

                for (i in 0 until numImages) {
                    val pStart = System.nanoTime()
                    val buf = ImagePreprocessor.preprocess(testBitmaps[i], modelConfig)
                    val pTime = (System.nanoTime() - pStart) / 1_000_000.0
                    preprocessedBuffers.add(buf)
                    preprocessTimes.add(pTime)
                }

                if (preprocessedBuffers.isEmpty()) {
                    val dummyBuffer = FloatBuffer.allocate(1 * modelConfig.inputChannels * modelConfig.inputHeight * modelConfig.inputWidth)
                    preprocessedBuffers.add(dummyBuffer)
                    preprocessTimes.add(0.0)
                }

                // Phase 2: Warm-up
                onProgress(
                    BenchmarkProgress(
                        currentModelIndex = index,
                        totalModels = totalModels,
                        currentModelName = modelConfig.displayName,
                        currentIteration = 0,
                        totalIterations = config.warmupIterations,
                        phase = Phase.WARMUP,
                        progressPercent = (index * 100) / totalModels
                    )
                )

                val warmupBuffer = preprocessedBuffers[0]

                for (w in 0 until config.warmupIterations) {
                    if (isCancelled) break
                    inferenceEngine.runInference(modelConfig.id, warmupBuffer, modelConfig)
                }

                if (isCancelled) {
                    inferenceEngine.unloadModel(modelConfig.id)
                    break
                }

                // Phase 3: Benchmarking
                val inferenceTimesMs = ArrayList<Double>(config.benchmarkIterations)
                var correctPredictions = 0
                val totalEvaluated = if (testLabels != null && testLabels.size >= numImages) numImages else 0

                val benchStartTime = SystemClock.elapsedRealtime()
                var peakMem = getMemoryUsageMB()

                val bufferCount = preprocessedBuffers.size
                for (iter in 0 until config.benchmarkIterations) {
                    if (isCancelled) break

                    val bufferIndex = iter % bufferCount
                    val inputBuf = preprocessedBuffers[bufferIndex]

                    val startNanos = System.nanoTime()
                    val logits = inferenceEngine.runInference(modelConfig.id, inputBuf, modelConfig)
                    val rawLatencyMs = (System.nanoTime() - startNanos) / 1_000_000.0

                    val latencyMs = if (config.deviceTier != DeviceHardwareTier.LIVE) {
                        val baseSim = rawLatencyMs * config.deviceTier.scaleFactor
                        val noise = (Math.random() - 0.5) * 0.12 * config.deviceTier.jitterFactor * baseSim
                        (baseSim + noise).coerceAtLeast(0.1)
                    } else {
                        rawLatencyMs
                    }

                    inferenceTimesMs.add(latencyMs)

                    // Track accuracy if labels provided
                    if (testLabels != null && bufferIndex < testLabels.size) {
                        val predictedClass = logits.indices.maxByOrNull { logits[it] } ?: -1
                        if (predictedClass == testLabels[bufferIndex]) {
                            correctPredictions++
                        }
                    }

                    // Sample memory occasionally
                    if (iter % 20 == 0) {
                        val curMem = getMemoryUsageMB() * config.deviceTier.ramScale
                        if (curMem > peakMem) peakMem = curMem
                    }

                    val overallProgress = ((index.toDouble() + (iter.toDouble() / config.benchmarkIterations)) / totalModels * 100).roundToInt()
                    onProgress(
                        BenchmarkProgress(
                            currentModelIndex = index,
                            totalModels = totalModels,
                            currentModelName = modelConfig.displayName,
                            currentIteration = iter + 1,
                            totalIterations = config.benchmarkIterations,
                            phase = Phase.BENCHMARKING,
                            progressPercent = overallProgress
                        )
                    )
                }

                val totalDurationMs = SystemClock.elapsedRealtime() - benchStartTime

                // Compute metrics
                val sortedTimes = inferenceTimesMs.sorted()
                val avgInference = if (inferenceTimesMs.isNotEmpty()) inferenceTimesMs.average() else 0.0
                val medianInference = if (sortedTimes.isNotEmpty()) {
                    if (sortedTimes.size % 2 == 0) {
                        (sortedTimes[sortedTimes.size / 2 - 1] + sortedTimes[sortedTimes.size / 2]) / 2.0
                    } else {
                        sortedTimes[sortedTimes.size / 2]
                    }
                } else 0.0
                val minInference = sortedTimes.firstOrNull() ?: 0.0
                val maxInference = sortedTimes.lastOrNull() ?: 0.0
                val p90Index = (sortedTimes.size * 0.90).toInt().coerceAtMost(sortedTimes.size - 1)
                val p90Inference = if (sortedTimes.isNotEmpty()) sortedTimes[p90Index] else 0.0

                val p95Index = (sortedTimes.size * 0.95).toInt().coerceAtMost(sortedTimes.size - 1)
                val p95Inference = if (sortedTimes.isNotEmpty()) sortedTimes[p95Index] else 0.0

                val p99Index = (sortedTimes.size * 0.99).toInt().coerceAtMost(sortedTimes.size - 1)
                val p99Inference = if (sortedTimes.isNotEmpty()) sortedTimes[p99Index] else 0.0

                val variance = if (inferenceTimesMs.isNotEmpty()) {
                    inferenceTimesMs.sumOf { Math.pow(it - avgInference, 2.0) } / inferenceTimesMs.size
                } else 0.0
                val stdDevInference = Math.sqrt(variance)

                val avgPreprocess = if (preprocessTimes.isNotEmpty()) preprocessTimes.average() else 0.0
                val throughput = if (avgInference > 0) 1000.0 / avgInference else 0.0
                val top1Acc = if (totalEvaluated > 0) (correctPredictions.toDouble() / totalEvaluated) * 100.0 else -1.0

                results.add(
                    BenchmarkMetrics(
                        modelId = modelConfig.id,
                        modelName = modelConfig.displayName,
                        modelSizeMB = modelSizeMB,
                        modelLoadTimeMs = loadTimeMs,
                        avgInferenceMs = avgInference,
                        medianInferenceMs = medianInference,
                        minInferenceMs = minInference,
                        maxInferenceMs = maxInference,
                        p90InferenceMs = p90Inference,
                        p95InferenceMs = p95Inference,
                        p99InferenceMs = p99Inference,
                        stdDevInferenceMs = stdDevInference,
                        allInferenceTimesMs = inferenceTimesMs,
                        avgPreprocessMs = avgPreprocess,
                        avgEndToEndMs = avgPreprocess + avgInference,
                        throughputImgPerSec = throughput,
                        peakMemoryMB = peakMem,
                        top1Accuracy = top1Acc,
                        correctPredictions = correctPredictions,
                        incorrectPredictions = totalEvaluated - correctPredictions,
                        totalEvaluated = totalEvaluated,
                        warmupIterations = config.warmupIterations,
                        benchmarkIterations = config.benchmarkIterations,
                        executionProvider = if (config.useNNAPI) "NNAPI" else "CPU",
                        numThreads = config.numThreads,
                        totalBenchmarkDurationMs = totalDurationMs,
                        deviceTier = config.deviceTier.displayName
                    )
                )

                // Clean up session between models to avoid memory contamination
                inferenceEngine.unloadModel(modelConfig.id)
                System.gc()

            } catch (e: Exception) {
                results.add(
                    createUnavailableMetrics(modelConfig, config, "Error: ${e.message}")
                )
                inferenceEngine.unloadModel(modelConfig.id)
            }
        }

        onProgress(
            BenchmarkProgress(
                currentModelIndex = totalModels,
                totalModels = totalModels,
                currentModelName = "",
                currentIteration = config.benchmarkIterations,
                totalIterations = config.benchmarkIterations,
                phase = Phase.COMPLETED,
                progressPercent = 100
            )
        )

        results
    }

    private fun getMemoryUsageMB(): Double {
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        return usedMem / (1024.0 * 1024.0)
    }

    private fun createUnavailableMetrics(
        modelConfig: ModelConfig,
        config: BenchmarkConfig,
        reason: String
    ): BenchmarkMetrics {
        return BenchmarkMetrics(
            modelId = modelConfig.id,
            modelName = modelConfig.displayName,
            modelSizeMB = 0.0,
            modelLoadTimeMs = 0.0,
            avgInferenceMs = 0.0,
            medianInferenceMs = 0.0,
            minInferenceMs = 0.0,
            maxInferenceMs = 0.0,
            p90InferenceMs = 0.0,
            allInferenceTimesMs = emptyList(),
            avgPreprocessMs = 0.0,
            avgEndToEndMs = 0.0,
            throughputImgPerSec = 0.0,
            peakMemoryMB = 0.0,
            top1Accuracy = -1.0,
            correctPredictions = 0,
            incorrectPredictions = 0,
            totalEvaluated = 0,
            warmupIterations = config.warmupIterations,
            benchmarkIterations = config.benchmarkIterations,
            executionProvider = if (config.useNNAPI) "NNAPI" else "CPU",
            numThreads = config.numThreads,
            totalBenchmarkDurationMs = 0L,
            warnings = listOf(reason)
        )
    }
}
