package com.mobilenetlab.engine

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import com.mobilenetlab.data.model.InferenceResult
import com.mobilenetlab.data.model.ModelConfig
import com.mobilenetlab.data.model.Prediction
import java.nio.FloatBuffer
import java.util.Collections

/**
 * ONNX Runtime inference engine.
 * Manages model sessions and executes inference with proper resource management.
 * Thread-safe for sequential model execution.
 */
class OnnxInferenceEngine(private val context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val loadedSessions = mutableMapOf<String, OrtSession>()
    private val labels: List<String> by lazy { loadLabels() }

    /**
     * Check if a model ONNX file exists in assets.
     */
    fun isModelAvailable(config: ModelConfig): Boolean {
        return try {
            context.assets.open(config.fileName).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the model file size in bytes. Returns -1 if unavailable.
     */
    fun getModelFileSize(config: ModelConfig): Long {
        return try {
            context.assets.openFd(config.fileName).use { it.length }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Load a model into an ONNX Runtime session.
     * Returns load time in milliseconds.
     */
    fun loadModel(config: ModelConfig, useNNAPI: Boolean = false, numThreads: Int = 4): Double {
        // Unload existing session if any
        unloadModel(config.id)

        val startTime = System.nanoTime()

        val options = SessionOptions().apply {
            setIntraOpNumThreads(numThreads)
            if (useNNAPI) {
                try {
                    addNnapi()
                } catch (e: Exception) {
                    // NNAPI not available, fall back to CPU silently
                }
            }
        }

        val modelBytes = context.assets.open(config.fileName).use { it.readBytes() }
        val session = ortEnv.createSession(modelBytes, options)

        val loadTimeMs = (System.nanoTime() - startTime) / 1_000_000.0

        synchronized(loadedSessions) {
            loadedSessions[config.id] = session
        }

        return loadTimeMs
    }

    /**
     * Check if a model is currently loaded.
     */
    fun isModelLoaded(modelId: String): Boolean {
        return synchronized(loadedSessions) { loadedSessions.containsKey(modelId) }
    }

    /**
     * Unload a model session, releasing resources.
     */
    fun unloadModel(modelId: String) {
        synchronized(loadedSessions) {
            loadedSessions.remove(modelId)?.close()
        }
    }

    /**
     * Run inference on a preprocessed input buffer.
     * Returns raw output logits as a FloatArray.
     * Does NOT include preprocessing time.
     */
    fun runInference(modelId: String, inputBuffer: FloatBuffer, config: ModelConfig): FloatArray {
        val session = synchronized(loadedSessions) {
            loadedSessions[modelId]
                ?: throw IllegalStateException("Model $modelId is not loaded")
        }

        val shape = longArrayOf(
            1L,
            config.inputChannels.toLong(),
            config.inputHeight.toLong(),
            config.inputWidth.toLong()
        )

        inputBuffer.rewind()
        val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape)

        val inputName = session.inputNames.first()
        val results = session.run(Collections.singletonMap(inputName, inputTensor))

        val outputTensor = results[0] as OnnxTensor
        @Suppress("UNCHECKED_CAST")
        val outputData = (outputTensor.value as Array<FloatArray>)[0]

        // Clean up
        inputTensor.close()
        results.close()

        return outputData
    }

    /**
     * Run full inference pipeline: preprocess + inference + postprocess.
     * Returns InferenceResult with timing breakdown.
     */
    fun runFullInference(
        modelId: String,
        bitmap: Bitmap,
        config: ModelConfig,
        topK: Int = 5
    ): InferenceResult {
        val totalStart = System.nanoTime()

        // Preprocess
        val preprocessStart = System.nanoTime()
        val inputBuffer = ImagePreprocessor.preprocess(bitmap, config)
        val preprocessTimeMs = (System.nanoTime() - preprocessStart) / 1_000_000.0

        // Inference
        val inferenceStart = System.nanoTime()
        val logits = runInference(modelId, inputBuffer, config)
        val inferenceTimeMs = (System.nanoTime() - inferenceStart) / 1_000_000.0

        val totalTimeMs = (System.nanoTime() - totalStart) / 1_000_000.0

        // Postprocess: softmax + top-k
        val predictions = postprocess(logits, topK)

        return InferenceResult(
            predictions = predictions,
            inferenceTimeMs = inferenceTimeMs,
            preprocessTimeMs = preprocessTimeMs,
            endToEndTimeMs = totalTimeMs,
            modelId = modelId
        )
    }

    /**
     * Get model input/output metadata from the session.
     */
    fun getModelMetadata(modelId: String): Map<String, String> {
        val session = synchronized(loadedSessions) { loadedSessions[modelId] } ?: return emptyMap()

        val info = mutableMapOf<String, String>()
        session.inputInfo.forEach { (name, nodeInfo) ->
            info["input_name"] = name
            info["input_shape"] = nodeInfo.info.toString()
        }
        session.outputInfo.forEach { (name, nodeInfo) ->
            info["output_name"] = name
            info["output_shape"] = nodeInfo.info.toString()
        }
        return info
    }

    /**
     * Convert logits to sorted predictions with softmax probabilities.
     */
    private fun postprocess(logits: FloatArray, topK: Int): List<Prediction> {
        // Softmax
        val maxLogit = logits.max()
        val exps = logits.map { Math.exp((it - maxLogit).toDouble()).toFloat() }
        val sumExps = exps.sum()
        val probs = exps.map { it / sumExps }

        // Sort by probability
        return probs.mapIndexed { index, prob ->
            Prediction(
                classIndex = index,
                className = if (index < labels.size) labels[index] else "class_$index",
                confidence = prob
            )
        }
            .sortedByDescending { it.confidence }
            .take(topK)
    }

    /**
     * Load CIFAR-100 class labels.
     */
    private fun loadLabels(): List<String> {
        return try {
            context.resources.getStringArray(
                context.resources.getIdentifier("cifar100_labels", "array", context.packageName)
            ).toList()
        } catch (e: Exception) {
            // Fallback: generate generic labels
            (0 until 100).map { "class_$it" }
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        synchronized(loadedSessions) {
            loadedSessions.values.forEach { it.close() }
            loadedSessions.clear()
        }
    }

    /**
     * Get ONNX Runtime version string.
     */
    fun getOrtVersion(): String {
        return "1.17.0"
    }
}
