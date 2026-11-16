package com.mobilenetlab.ui.dashboard

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mobilenetlab.R
import com.mobilenetlab.data.model.BenchmarkMetrics
import com.mobilenetlab.data.model.ModelConfig
import com.mobilenetlab.databinding.FragmentDashboardBinding
import com.mobilenetlab.ui.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDeviceInfo()
        setupAvailableModelsCount()
        setupBenchmarkRunsCount()
        setupLatestSummary()

        binding.btnQuickBenchmark.setOnClickListener {
            safelyNavigateTo(R.id.navigation_benchmark)
        }

        binding.cardModelsCount.setOnClickListener {
            safelyNavigateTo(R.id.navigation_models)
        }

        binding.cardBenchmarkCount.setOnClickListener {
            safelyNavigateTo(R.id.navigation_results)
        }

        binding.cardLatestSummary.setOnClickListener {
            val dest = if (sharedViewModel.latestMetrics.value.isNullOrEmpty()) {
                R.id.navigation_benchmark
            } else {
                R.id.navigation_results
            }
            safelyNavigateTo(dest)
        }
    }

    private fun safelyNavigateTo(destinationId: Int) {
        val mainActivity = activity as? com.mobilenetlab.ui.MainActivity
        if (mainActivity != null) {
            mainActivity.navigateToTab(destinationId)
        } else {
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.navigation_dashboard) {
                try {
                    navController.navigate(destinationId)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun setupDeviceInfo() {
        val dev = sharedViewModel.deviceInfo
        binding.tvDeviceName.text = "${dev.manufacturer} ${dev.model}"
        binding.tvAndroidVersion.text = "Android ${dev.androidVersion} (API ${dev.apiLevel})" +
                if (dev.isEmulator) " [Emulator]" else ""
        binding.tvCpuArch.text = "Arch: ${dev.cpuArchitecture} (${dev.numCores} cores)"
        binding.tvRam.text = "RAM: ${dev.availableRamMB}/${dev.totalRamMB} MB"
        binding.tvOnnxVersion.text = dev.onnxRuntimeVersion
    }

    private fun setupAvailableModelsCount() {
        var availableCount = 0
        for (m in ModelConfig.ALL_MODELS) {
            if (sharedViewModel.inferenceEngine.isModelAvailable(m)) {
                availableCount++
            }
        }
        binding.tvModelsCount.text = "$availableCount/${ModelConfig.ALL_MODELS.size}"
    }

    private fun setupBenchmarkRunsCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.sessionCount.collectLatest { count ->
                binding.tvBenchmarkCount.text = count.toString()
            }
        }
    }

    private fun setupLatestSummary() {
        sharedViewModel.latestMetrics.observe(viewLifecycleOwner) { metrics ->
            if (metrics.isNullOrEmpty()) {
                binding.tvLatestSummary.visibility = View.VISIBLE
                binding.tvLatestSummary.text = getString(R.string.no_benchmarks_yet)
                binding.tableSummary.visibility = View.GONE
            } else {
                binding.tvLatestSummary.visibility = View.GONE
                binding.tableSummary.visibility = View.VISIBLE
                populateSummaryTable(metrics)
            }
        }
    }

    private fun populateSummaryTable(metrics: List<BenchmarkMetrics>) {
        binding.tableSummary.removeAllViews()

        val hasAcc = metrics.any { it.totalEvaluated > 0 && it.top1Accuracy >= 0 }

        // Header Row
        val headerRow = TableRow(requireContext())
        headerRow.addView(createCell("Model", isHeader = true))
        headerRow.addView(createCell("Avg Latency", isHeader = true))
        headerRow.addView(createCell("Throughput", isHeader = true))
        if (hasAcc) {
            headerRow.addView(createCell("Accuracy", isHeader = true))
        }
        binding.tableSummary.addView(headerRow)

        // Data Rows
        for (m in metrics) {
            val row = TableRow(requireContext())
            row.addView(createCell(m.modelName, isHeader = false))
            val latText = if (m.avgInferenceMs > 0) String.format(Locale.US, "%.1f ms", m.avgInferenceMs) else "N/A"
            row.addView(createCell(latText, isHeader = false))
            val tpText = if (m.throughputImgPerSec > 0) String.format(Locale.US, "%.1f/s", m.throughputImgPerSec) else "N/A"
            row.addView(createCell(tpText, isHeader = false))
            if (hasAcc) {
                val accText = String.format(Locale.US, "%.1f%%", m.top1Accuracy)
                row.addView(createCell(accText, isHeader = false))
            }
            binding.tableSummary.addView(row)
        }
    }

    private fun createCell(text: String, isHeader: Boolean): TextView {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setPadding(8, 8, 8, 8)
        tv.gravity = Gravity.CENTER_VERTICAL
        if (isHeader) {
            tv.setTypeface(null, Typeface.BOLD)
            tv.setTextColor(Color.DKGRAY)
            tv.textSize = 12f
        } else {
            tv.textSize = 13f
            tv.setTextColor(Color.BLACK)
        }
        return tv
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
