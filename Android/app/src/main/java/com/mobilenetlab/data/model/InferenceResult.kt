package com.mobilenetlab.data.model

/**
 * Result of a single image inference.
 */
data class InferenceResult(
    val predictions: List<Prediction>,
    val inferenceTimeMs: Double,
    val preprocessTimeMs: Double,
    val endToEndTimeMs: Double,
    val modelId: String
)

data class Prediction(
    val classIndex: Int,
    val className: String,
    val confidence: Float
)
