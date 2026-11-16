package com.mobilenetlab.ui.models

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
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

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            testDialogBinding?.ivSelectedImage?.setImageBitmap(selectedBitmap)
            testDialogBinding?.btnRunSingleInference?.isEnabled = true
        }
    }

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

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePhotoLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchGalleryPicker()
        } else {
            Toast.makeText(context, "Storage permission required to choose images", Toast.LENGTH_SHORT).show()
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
                    cardBinding.btnTestInference.isEnabled = true
                } else {
                    cardBinding.chipStatus.text = getString(R.string.model_not_loaded)
                    cardBinding.chipStatus.setChipBackgroundColorResource(R.color.secondary_container)
                    cardBinding.chipStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_secondary_container))
                    cardBinding.btnLoadUnload.text = getString(R.string.load_model)
                    cardBinding.btnTestInference.isEnabled = false
                }

                cardBinding.btnLoadUnload.isEnabled = true
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
                        try {
                            withContext(Dispatchers.IO) {
                                sharedViewModel.inferenceEngine.loadModel(config)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        if (isAdded) populateModelCards()
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

        val isLoaded = sharedViewModel.inferenceEngine.isModelLoaded(config.id)
        val statusText = if (isLoaded) "Status: Loaded in Memory" else "Status: Not Loaded (Click 'Load Model' on Card First)"

        dialogView.tvDialogModelName.text = "Inference: ${config.displayName}"
        dialogView.tvModelDetails.text = "Architecture: ${config.architecture}\nParams: ${config.paramsMillions}M | MACs: ${config.macsGiga}G\n$statusText"

        dialogView.btnSelectImage.setOnClickListener {
            showImageSourceChooser()
        }

        dialogView.btnRunSingleInference.setOnClickListener {
            val bmp = selectedBitmap
            if (bmp == null) {
                Toast.makeText(context, "Select an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!sharedViewModel.inferenceEngine.isModelLoaded(config.id)) {
                Toast.makeText(
                    context,
                    "Model is not loaded. Please click 'Load Model' on the model card first.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            dialogView.progressBarInference.visibility = View.VISIBLE
            dialogView.btnRunSingleInference.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        sharedViewModel.inferenceEngine.runFullInference(config.id, bmp, config)
                    }

                    if (!isAdded) return@launch

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
                } catch (e: Exception) {
                    if (isAdded) {
                        dialogView.progressBarInference.visibility = View.GONE
                        dialogView.btnRunSingleInference.isEnabled = true
                        Toast.makeText(context, "Inference error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView.root)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showImageSourceChooser() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.pick_gallery)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_image_source))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkAndLaunchCamera()
                    1 -> checkAndLaunchGallery()
                }
            }
            .show()
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhotoLauncher.launch(null)
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkAndLaunchGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            launchGalleryPicker()
        } else {
            requestGalleryPermissionLauncher.launch(permission)
        }
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        testDialogBinding = null
    }
}
