package com.mobilenetlab.engine

import android.content.Context
import android.graphics.Bitmap
import com.mobilenetlab.data.model.ModelConfig
import java.nio.FloatBuffer

/**
 * Image preprocessor for model inference.
 * Implements center crop + resize + normalization matching the timm training pipeline.
 *
 * For all three models (trained via timm with ImageNet pretrained weights):
 * - Resize with bicubic interpolation
 * - Center crop with crop_pct = 0.875
 * - Normalize with ImageNet mean/std
 * - Output: CHW float tensor (channels-first)
 */
object ImagePreprocessor {

    /**
     * Preprocess a Bitmap for model inference.
     * Returns a FloatBuffer in NCHW format (batch=1, channels=3, height, width).
     *
     * Steps:
     * 1. Center crop the image based on crop percentage
     * 2. Resize to model input dimensions (bilinear in Android, closest to bicubic available)
     * 3. Normalize pixel values with mean/std
     * 4. Convert to CHW layout
     */
    fun preprocess(bitmap: Bitmap, config: ModelConfig): FloatBuffer {
        val targetW = config.inputWidth
        val targetH = config.inputHeight

        // Step 1: Center crop
        val cropped = centerCrop(bitmap, config.cropPercentage)

        // Step 2: Resize to target dimensions
        val resized = Bitmap.createScaledBitmap(cropped, targetW, targetH, true) // bilinear filter

        // Step 3 & 4: Extract pixels, normalize, convert to CHW
        val buffer = FloatBuffer.allocate(1 * config.inputChannels * targetH * targetW)
        val pixels = IntArray(targetW * targetH)
        resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        val mean = config.meanValues
        val std = config.stdValues

        // CHW layout: all R values, then all G values, then all B values
        for (c in 0 until 3) {
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> ((pixel shr 16) and 0xFF) / 255.0f  // R
                    1 -> ((pixel shr 8) and 0xFF) / 255.0f   // G
                    2 -> (pixel and 0xFF) / 255.0f            // B
                    else -> 0f
                }
                buffer.put((value - mean[c]) / std[c])
            }
        }
        buffer.rewind()

        // Clean up temporary bitmaps
        if (cropped !== bitmap) cropped.recycle()
        if (resized !== cropped) resized.recycle()

        return buffer
    }

    /**
     * Center crop a bitmap based on crop percentage.
     * crop_pct = 0.875 means we keep the center 87.5% of the shorter dimension.
     */
    private fun centerCrop(bitmap: Bitmap, cropPct: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val minDim = minOf(w, h)
        val cropSize = (minDim * cropPct).toInt()

        val x = (w - cropSize) / 2
        val y = (h - cropSize) / 2

        return Bitmap.createBitmap(bitmap, x, y, cropSize, cropSize)
    }

    /**
     * Load a test image from assets.
     */
    fun loadBitmapFromAssets(context: Context, path: String): Bitmap? {
        return try {
            context.assets.open(path).use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
