package com.mobilenetlab.ui.models

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.mobilenetlab.R
import com.mobilenetlab.data.model.ModelConfig
import com.mobilenetlab.databinding.DialogInferenceTestBinding
import com.mobilenetlab.databinding.FragmentModelsBinding
import com.mobilenetlab.databinding.ItemModelCardBinding
import com.mobilenetlab.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: MainViewModel by activityViewModels()

    private var activeTestModel: ModelConfig? = null
    private var testDialogBinding: DialogInferenceTestBinding? = null
    private var selectedBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                try {
                    val stream = requireContext().contentResolver.openInputStream(imageUri)
                    selectedBitmap = BitmapFactory.decodeStream(stream)
                    testDialogBinding?.ivSelectedImage?.setImageBitmap(selectedBitmap)
                    testDialogBinding?.btnRunSingleInference?.isEnabled = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateModelCards()
    }

    private fun populateModelCards() {
        binding.modelCardsContainer.removeAllViews()

        for (config in ModelConfig.ALL_MODELS) {
            val cardBinding = ItemModelCardBinding.inflate(
                layoutInflater,
                binding.modelCardsContainer,
                false
            )

            // Name & Description
            cardBinding.tvModelName.text = config.displayName
            cardBinding.tvModelStatus.text = config.architecture

            // Accent bar color
            val accentColor = when (config.id) {
                "conv_small" -> ContextCompat.getColor(requireContext(), R.color.model_conv_small)
                "hybrid_medium" -> ContextCompat.getColor(requireContext(), R.color.model_hybrid_medium)
                else -> ContextCompat.getColor(requireContext(), R.color.model_mobilenetv2)
            }
            cardBinding.accentBar.setBackgroundColor(accentColor)

            // Availability & Specs
            val isAvailable = sharedViewModel.inferenceEngine.isModelAvailable(config)
            val isLoaded = sharedViewModel.inferenceEngine.isModelLoaded(config.id)

            if (isAvailable) {
                val sizeBytes = sharedViewModel.inferenceEngine.getModelFileSize(config)
                val sizeMB = if (sizeBytes > 0) String.format(Locale.US, "%.2f MB", sizeBytes / (1024.0 * 1024.0)) else "Ready"
                cardBinding.tvFileSize.text = sizeMB
                cardBinding.tvInputRes.text = "${config.inputWidth}×${config.inputHeight}"
                cardBinding.tvClasses.text = "${config.numClasses}"

                if (isLoaded) {
                    cardBinding.chipStatus.text = getString(R.string.model_loaded)
                    cardBinding.chipStatus.setChipBackgroundColorResource(R.color.success_container)
                    cardBinding.chipStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
                    cardBinding.btnLoadUnload.text = getString(R.string.unload_model)
                } else {
                    cardBinding.chipStatus.text = getString(R.string.model_not_loaded)
                    cardBinding.chipStatus.setChipBackgroundColorResource(R.color.secondary_container)
                    cardBinding.chipStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_secondary_container))
                    cardBinding.btnLoadUnload.text = getString(R.string.load_model)
                }

                cardBinding.btnLoadUnload.isEnabled = true
                cardBinding.btnTestInference.isEnabled = true
            } else {
                cardBinding.tvFileSize.text = "Missing"
                cardBinding.tvInputRes.text = "${config.inputWidth}×${config.inputHeight}"
                cardBinding.tvClasses.text = "${config.numClasses}"

                cardBinding.chipStatus.text = getString(R.string.model_unavailable)
                cardBinding.chipStatus.setChipBackgroundColorResource(R.color.error_container)
                cardBinding.chipStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))

                cardBinding.btnLoadUnload.isEnabled = false
                cardBinding.btnTestInference.isEnabled = false
            }

            // Load/Unload Action
            cardBinding.btnLoadUnload.setOnClickListener {
                if (sharedViewModel.inferenceEngine.isModelLoaded(config.id)) {
                    sharedViewModel.inferenceEngine.unloadModel(config.id)
                    populateModelCards()
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        cardBinding.btnLoadUnload.isEnabled = false
                        withContext(Dispatchers.IO) {
                            sharedViewModel.inferenceEngine.loadModel(config)
                        }
                        populateModelCards()
                    }
                }
            }

            // Test Inference Action
            cardBinding.btnTestInference.setOnClickListener {
                showInferenceTestDialog(config)
            }

            binding.modelCardsContainer.addView(cardBinding.root)
        }
    }

    private fun showInferenceTestDialog(config: ModelConfig) {
        activeTestModel = config
        val dialogView = DialogInferenceTestBinding.inflate(layoutInflater)
        testDialogBinding = dialogView

        dialogView.tvDialogModelName.text = "Inference: ${config.displayName}"
        dialogView.tvModelDetails.text = "Architecture: ${config.architecture}\nTarget: CIFAR-100 (100 classes)\nNormalization: ImageNet (Mean: 0.485, Std: 0.229)"

        dialogView.btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        dialogView.btnRunSingleInference.setOnClickListener {
            val bmp = selectedBitmap
            if (bmp == null) {
                Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogView.progressBarInference.visibility = View.VISIBLE
            dialogView.btnRunSingleInference.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    if (!sharedViewModel.inferenceEngine.isModelLoaded(config.id)) {
                        sharedViewModel.inferenceEngine.loadModel(config)
                    }
                    sharedViewModel.inferenceEngine.runFullInference(config.id, bmp, config)
                }

                dialogView.progressBarInference.visibility = View.GONE
                dialogView.btnRunSingleInference.isEnabled = true

                // Display timings
                dialogView.tvTimingResults.text = String.format(
                    Locale.US,
                    "Preprocess: %.2f ms | Pure Inference: %.2f ms | Total: %.2f ms",
                    result.preprocessTimeMs,
                    result.inferenceTimeMs,
                    result.endToEndTimeMs
                )

                // Display predictions
                val sb = StringBuilder()
                for ((rank, p) in result.predictions.withIndex()) {
                    sb.append(String.format(Locale.US, "#%d: %s (%.1f%%)\n", rank + 1, p.className, p.confidence * 100))
                }
                dialogView.tvPredictions.text = sb.toString().trimEnd()
            }
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView.root)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        testDialogBinding = null
    }
}
