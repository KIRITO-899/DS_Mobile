package com.mobilenetlab.data.model

/**
 * Defines a model that can be benchmarked.
 * Each model has its own preprocessing configuration based on its training setup.
 */
data class ModelConfig(
    val id: String,
    val displayName: String,
    val fileName: String,
    val inputWidth: Int = 224,
    val inputHeight: Int = 224,
    val inputChannels: Int = 3,
    val numClasses: Int = 100,
    val meanValues: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f),
    val stdValues: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f),
    val cropPercentage: Float = 0.875f,
    val description: String = "",
    val architecture: String = "",
    val paramsMillions: Double = 0.0,
    val macsGiga: Double = 0.0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelConfig) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /**
         * MobileNetV4-Conv-Small: Pure CNN, UIB blocks only, no attention.
         * Trained on CIFAR-100 with ImageNet pretrained weights from timm.
         * timm model: mobilenetv4_conv_small.e2400_r224_in1k
         * Preprocessing: ImageNet normalization, bicubic resize, center crop 0.875.
         */
        val CONV_SMALL = ModelConfig(
            id = "conv_small",
            displayName = "MobileNetV4-Conv-Small",
            fileName = "mobilenetv4_conv_small_cifar100.onnx",
            meanValues = floatArrayOf(0.485f, 0.456f, 0.406f),
            stdValues = floatArrayOf(0.229f, 0.224f, 0.225f),
            cropPercentage = 0.875f,
            description = "Pure CNN with Universal Inverted Bottleneck blocks. 2.62M parameters, 0.185G MACs.",
            architecture = "UIB (Conv only)",
            paramsMillions = 2.62,
            macsGiga = 0.185
        )

        /**
         * MobileNetV4-Hybrid-Medium: UIB blocks + Mobile Multi-Query Attention.
         * Trained on CIFAR-100 with ImageNet pretrained weights from timm.
         */
        val HYBRID_MEDIUM = ModelConfig(
            id = "hybrid_medium",
            displayName = "MobileNetV4-Hybrid-Medium",
            fileName = "mobilenetv4_conv_medium_cifar100.onnx",
            meanValues = floatArrayOf(0.485f, 0.456f, 0.406f),
            stdValues = floatArrayOf(0.229f, 0.224f, 0.225f),
            cropPercentage = 0.875f,
            description = "UIB blocks + Mobile MQA attention layers. 11.08M parameters, 0.52G MACs.",
            architecture = "UIB + Mobile MQA (Hybrid)",
            paramsMillions = 11.08,
            macsGiga = 0.52
        )

        /**
         * MobileNetV2: Classic inverted residual architecture, older generation baseline.
         */
        val MOBILENET_V2 = ModelConfig(
            id = "mobilenetv2",
            displayName = "MobileNetV2",
            fileName = "mobilenetv2_cifar100.onnx",
            meanValues = floatArrayOf(0.485f, 0.456f, 0.406f),
            stdValues = floatArrayOf(0.229f, 0.224f, 0.225f),
            cropPercentage = 0.875f,
            description = "Classic inverted residual architecture baseline. 3.50M parameters, 0.30G MACs.",
            architecture = "Inverted Residual (V2)",
            paramsMillions = 3.50,
            macsGiga = 0.30
        )

        val ALL_MODELS = listOf(CONV_SMALL, HYBRID_MEDIUM, MOBILENET_V2)
    }
}
