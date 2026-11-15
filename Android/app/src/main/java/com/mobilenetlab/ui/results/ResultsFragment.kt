package com.mobilenetlab.ui.results

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.mobilenetlab.R
import com.mobilenetlab.data.model.BenchmarkMetrics
import com.mobilenetlab.databinding.FragmentResultsBinding
import com.mobilenetlab.ui.MainViewModel
import com.mobilenetlab.util.ExportUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!
    private val sharedViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeMetrics()
        setupExportButtons()
        setupSessionSelector()
    }

    private fun observeMetrics() {
        sharedViewModel.latestMetrics.observe(viewLifecycleOwner) { metrics ->
            if (metrics.isNullOrEmpty()) {
                binding.tvNoResults.visibility = View.VISIBLE
                binding.resultsContent.visibility = View.GONE
            } else {
                binding.tvNoResults.visibility = View.GONE
                binding.resultsContent.visibility = View.VISIBLE

                populateComparisonTable(metrics)
                renderCharts(metrics)
                renderAnalysis(metrics)
            }
        }
    }

    private fun populateComparisonTable(metrics: List<BenchmarkMetrics>) {
        binding.tableComparison.removeAllViews()

        // Headers: Metric, [Model 1], [Model 2], [Model 3]
        val headerRow = TableRow(requireContext())
        headerRow.addView(createCell("Metric", isHeader = true))
        for (m in metrics) {
            headerRow.addView(createCell(m.modelName, isHeader = true))
        }
        binding.tableComparison.addView(headerRow)

        // Metrics list to render
        val metricRows = listOf(
            "Model Size" to metrics.map { String.format(Locale.US, "%.2f MB", it.modelSizeMB) },
            "Load Time" to metrics.map { String.format(Locale.US, "%.1f ms", it.modelLoadTimeMs) },
            "Avg Latency" to metrics.map { String.format(Locale.US, "%.2f ms", it.avgInferenceMs) },
            "Median Latency" to metrics.map { String.format(Locale.US, "%.2f ms", it.medianInferenceMs) },
            "P90 Latency" to metrics.map { String.format(Locale.US, "%.2f ms", it.p90InferenceMs) },
            "Min Latency" to metrics.map { String.format(Locale.US, "%.2f ms", it.minInferenceMs) },
            "Max Latency" to metrics.map { String.format(Locale.US, "%.2f ms", it.maxInferenceMs) },
            "Throughput" to metrics.map { String.format(Locale.US, "%.1f img/s", it.throughputImgPerSec) },
            "Peak Memory" to metrics.map { String.format(Locale.US, "%.1f MB", it.peakMemoryMB) },
            "Top-1 Accuracy" to metrics.map { if (it.top1Accuracy >= 0) String.format(Locale.US, "%.2f%%", it.top1Accuracy) else "N/A" },
            "Provider" to metrics.map { it.executionProvider }
        )

        for ((label, values) in metricRows) {
            val row = TableRow(requireContext())
            row.addView(createCell(label, isHeader = true))
            for (v in values) {
                row.addView(createCell(v, isHeader = false))
            }
            binding.tableComparison.addView(row)
        }
    }

    private fun renderCharts(metrics: List<BenchmarkMetrics>) {
        val validMetrics = metrics.filter { it.avgInferenceMs > 0 }
        if (validMetrics.isEmpty()) return

        val labels = validMetrics.map { it.modelName }
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.chart_blue),
            ContextCompat.getColor(requireContext(), R.color.chart_purple),
            ContextCompat.getColor(requireContext(), R.color.chart_green)
        )

        // 1. Latency Chart
        val latencyEntries = validMetrics.mapIndexed { i, m -> BarEntry(i.toFloat(), m.avgInferenceMs.toFloat()) }
        val latencySet = BarDataSet(latencyEntries, "Avg Latency (ms)").apply {
            setColors(colors.take(validMetrics.size))
            valueTextSize = 11f
        }
        binding.chartLatency.apply {
            data = BarData(latencySet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            animateY(500)
            invalidate()
        }

        // 2. Accuracy Chart
        val accuracyEntries = validMetrics.mapIndexed { i, m ->
            BarEntry(i.toFloat(), if (m.top1Accuracy >= 0) m.top1Accuracy.toFloat() else 0f)
        }
        val accuracySet = BarDataSet(accuracyEntries, "Top-1 Accuracy (%)").apply {
            setColors(colors.take(validMetrics.size))
            valueTextSize = 11f
        }
        binding.chartAccuracy.apply {
            data = BarData(accuracySet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            animateY(500)
            invalidate()
        }

        // 3. Throughput Chart
        val tpEntries = validMetrics.mapIndexed { i, m -> BarEntry(i.toFloat(), m.throughputImgPerSec.toFloat()) }
        val tpSet = BarDataSet(tpEntries, "Throughput (img/s)").apply {
            setColors(colors.take(validMetrics.size))
            valueTextSize = 11f
        }
        binding.chartThroughput.apply {
            data = BarData(tpSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            animateY(500)
            invalidate()
        }
    }

    private fun renderAnalysis(metrics: List<BenchmarkMetrics>) {
        binding.analysisContainer.removeAllViews()

        val validMetrics = metrics.filter { it.avgInferenceMs > 0 }
        if (validMetrics.isEmpty()) return

        val fastest = validMetrics.minByOrNull { it.avgInferenceMs }
        val smallest = validMetrics.minByOrNull { it.modelSizeMB }
        val mostAccurate = validMetrics.filter { it.top1Accuracy >= 0 }.maxByOrNull { it.top1Accuracy }

        addAnalysisRow("⚡ Fastest Model:", "${fastest?.modelName} (${String.format(Locale.US, "%.1f ms", fastest?.avgInferenceMs)})")
        addAnalysisRow("📦 Smallest Model:", "${smallest?.modelName} (${String.format(Locale.US, "%.2f MB", smallest?.modelSizeMB)})")
        if (mostAccurate != null) {
            addAnalysisRow("🎯 Most Accurate:", "${mostAccurate.modelName} (${String.format(Locale.US, "%.1f%%", mostAccurate.top1Accuracy)})")
        }

        // Transparent trade-off summary
        val summary = StringBuilder()
        summary.append("Architectural Trade-Off Analysis:\n")
        summary.append("• MobileNetV4-Conv-Small uses Universal Inverted Bottleneck blocks without attention, optimizing for CPU latency.\n")
        summary.append("• MobileNetV4-Hybrid-Medium incorporates Mobile MQA, which increases compute but targets improved representational capacity.\n")
        summary.append("• MobileNetV2 serves as the legacy baseline demonstrating the generational gain of UIB blocks.")
        addAnalysisRow("📊 Recommendation:", summary.toString())
    }

    private fun addAnalysisRow(title: String, content: String) {
        val tv = TextView(requireContext())
        tv.text = "$title\n$content\n"
        tv.textSize = 13f
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
        binding.analysisContainer.addView(tv)
    }

    private fun setupExportButtons() {
        binding.btnExportCsv.setOnClickListener {
            val metrics = sharedViewModel.latestMetrics.value
            if (metrics.isNullOrEmpty()) return@setOnClickListener

            val file = ExportUtils.exportToCsv(requireContext(), metrics, sharedViewModel.deviceInfo)
            Toast.makeText(context, "Exported CSV to: ${file.name}", Toast.LENGTH_LONG).show()
        }

        binding.btnExportJson.setOnClickListener {
            val metrics = sharedViewModel.latestMetrics.value
            if (metrics.isNullOrEmpty()) return@setOnClickListener

            val file = ExportUtils.exportToJson(requireContext(), metrics, sharedViewModel.deviceInfo)
            Toast.makeText(context, "Exported JSON to: ${file.name}", Toast.LENGTH_LONG).show()
        }

        binding.btnDeleteSession.setOnClickListener {
            // Delete current session logic if needed
            Toast.makeText(context, "Session management available in session picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSessionSelector() {
        binding.btnSelectSession.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val sessions = sharedViewModel.allSessions.value
                if (sessions.isEmpty()) {
                    Toast.makeText(context, "No saved sessions found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val items = sessions.map { s ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(s.timestamp))
                    "$date - ${s.executionProvider} (${s.benchmarkIterations} iters)"
                }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Select Benchmark Session")
                    .setItems(items) { _, which ->
                        val selected = sessions[which]
                        sharedViewModel.loadSessionById(selected.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun createCell(text: String, isHeader: Boolean): TextView {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setPadding(12, 10, 12, 10)
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
