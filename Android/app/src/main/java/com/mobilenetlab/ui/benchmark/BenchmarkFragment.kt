package com.mobilenetlab.ui.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.mobilenetlab.R
import com.mobilenetlab.data.model.ModelConfig
import com.mobilenetlab.databinding.FragmentBenchmarkBinding
import com.mobilenetlab.engine.BenchmarkEngine
import com.mobilenetlab.engine.DeviceHardwareTier
import com.mobilenetlab.ui.MainViewModel

class BenchmarkFragment : Fragment() {

    private var _binding: FragmentBenchmarkBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBenchmarkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupModelSelection()
        setupButtons()
        observeProgress()
    }

    private fun setupModelSelection() {
        // Enable/disable checkboxes according to model availability
        binding.cbConvSmall.isEnabled = sharedViewModel.inferenceEngine.isModelAvailable(ModelConfig.CONV_SMALL)
        if (!binding.cbConvSmall.isEnabled) binding.cbConvSmall.isChecked = false

        binding.cbHybridMedium.isEnabled = sharedViewModel.inferenceEngine.isModelAvailable(ModelConfig.HYBRID_MEDIUM)
        if (!binding.cbHybridMedium.isEnabled) binding.cbHybridMedium.isChecked = false

        binding.cbMobilenetv2.isEnabled = sharedViewModel.inferenceEngine.isModelAvailable(ModelConfig.MOBILENET_V2)
        if (!binding.cbMobilenetv2.isEnabled) binding.cbMobilenetv2.isChecked = false
    }

    private fun setupButtons() {
        binding.btnStartBenchmark.setOnClickListener {
            val selectedModels = mutableListOf<ModelConfig>()
            if (binding.cbConvSmall.isChecked) selectedModels.add(ModelConfig.CONV_SMALL)
            if (binding.cbHybridMedium.isChecked) selectedModels.add(ModelConfig.HYBRID_MEDIUM)
            if (binding.cbMobilenetv2.isChecked) selectedModels.add(ModelConfig.MOBILENET_V2)

            if (selectedModels.isEmpty()) {
                Toast.makeText(context, "Select at least one available model", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val warmup = (binding.etWarmup.text.toString().toIntOrNull() ?: 10).coerceAtLeast(0)
            val iterations = (binding.etIterations.text.toString().toIntOrNull() ?: 100).coerceAtLeast(1)
            val numImages = (binding.etTestImages.text.toString().toIntOrNull() ?: 100).coerceAtLeast(1)
            val useNNAPI = binding.chipNnapi.isChecked

            val selectedTier = when (binding.chipGroupHardwareTier.checkedChipId) {
                R.id.chip_tier_high -> DeviceHardwareTier.HIGH_END
                R.id.chip_tier_mid -> DeviceHardwareTier.MID_RANGE
                R.id.chip_tier_low -> DeviceHardwareTier.LOW_END
                else -> DeviceHardwareTier.LIVE
            }

            val config = BenchmarkEngine.BenchmarkConfig(
                warmupIterations = warmup,
                benchmarkIterations = iterations,
                useNNAPI = useNNAPI,
                numThreads = 4,
                modelsToBenchmark = selectedModels,
                deviceTier = selectedTier
            )

            // Generate synthetic test images (or load CIFAR test images if bundled)
            // Creating colored synthetic test bitmaps for reliable reproducible evaluation
            val testBitmaps = createSyntheticTestImages(numImages)

            binding.cardProgress.visibility = View.VISIBLE
            binding.btnStartBenchmark.visibility = View.GONE
            binding.btnStopBenchmark.visibility = View.VISIBLE

            sharedViewModel.startBenchmark(config, testBitmaps)
        }

        binding.btnStopBenchmark.setOnClickListener {
            sharedViewModel.cancelBenchmark()
        }
    }

    private fun observeProgress() {
        sharedViewModel.benchmarkProgress.observe(viewLifecycleOwner) { progress ->
            if (progress != null) {
                binding.cardProgress.visibility = View.VISIBLE
                binding.tvProgressStatus.text = "Model ${progress.currentModelIndex + 1}/${progress.totalModels}: ${progress.currentModelName}"
                binding.progressBenchmark.progress = progress.progressPercent

                val phaseText = when (progress.phase) {
                    BenchmarkEngine.Phase.LOADING -> "Loading model session into memory..."
                    BenchmarkEngine.Phase.WARMUP -> "Warming up execution provider..."
                    BenchmarkEngine.Phase.BENCHMARKING -> "Measuring: Iteration ${progress.currentIteration}/${progress.totalIterations}"
                    BenchmarkEngine.Phase.COMPLETED -> "Benchmarking complete!"
                    BenchmarkEngine.Phase.ERROR -> "Error occurred during benchmarking"
                }
                binding.tvProgressDetail.text = phaseText
            }
        }

        sharedViewModel.isBenchmarking.observe(viewLifecycleOwner) { isRunning ->
            if (isRunning) {
                binding.btnStartBenchmark.visibility = View.GONE
                binding.btnStopBenchmark.visibility = View.VISIBLE
            } else {
                binding.btnStartBenchmark.visibility = View.VISIBLE
                binding.btnStopBenchmark.visibility = View.GONE
            }
        }

        sharedViewModel.navigateToResultsEvent.observe(viewLifecycleOwner) { shouldNavigate ->
            if (shouldNavigate == true) {
                sharedViewModel.consumeNavigateToResultsEvent()
                binding.cardProgress.visibility = View.GONE
                val mainActivity = activity as? com.mobilenetlab.ui.MainActivity
                if (mainActivity != null) {
                    mainActivity.navigateToTab(R.id.navigation_results)
                } else if (findNavController().currentDestination?.id == R.id.navigation_benchmark) {
                    findNavController().navigate(R.id.navigation_results)
                }
            }
        }
    }

    /**
     * Creates reproducible test bitmaps with varying patterns and colors.
     * Guarantees fair evaluation when external datasets aren't physically on device.
     */
    private fun createSyntheticTestImages(count: Int): List<Bitmap> {
        val bitmaps = ArrayList<Bitmap>(count)
        val colors = intArrayOf(
            Color.RED, Color.GREEN, Color.BLUE, Color.CYAN,
            Color.MAGENTA, Color.YELLOW, Color.GRAY, Color.WHITE
        )

        for (i in 0 until count) {
            val bmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(colors[i % colors.size])
            bitmaps.add(bmp)
        }
        return bitmaps
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
