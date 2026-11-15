package com.mobilenetlab.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mobilenetlab.data.local.AppDatabase
import com.mobilenetlab.data.local.BenchmarkSessionEntity
import com.mobilenetlab.data.model.BenchmarkMetrics
import com.mobilenetlab.data.model.DeviceInfo
import com.mobilenetlab.data.model.ModelConfig
import com.mobilenetlab.engine.BenchmarkEngine
import com.mobilenetlab.engine.OnnxInferenceEngine
import com.mobilenetlab.util.DeviceUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val inferenceEngine = OnnxInferenceEngine(application)
    val benchmarkEngine = BenchmarkEngine(application, inferenceEngine)
    val database = AppDatabase.getDatabase(application)
    val dao = database.benchmarkSessionDao()

    val deviceInfo: DeviceInfo by lazy {
        DeviceUtils.getDeviceInfo(application, inferenceEngine.getOrtVersion())
    }

    private val _latestMetrics = MutableLiveData<List<BenchmarkMetrics>?>()
    val latestMetrics: LiveData<List<BenchmarkMetrics>?> = _latestMetrics

    private val _benchmarkProgress = MutableLiveData<BenchmarkEngine.BenchmarkProgress?>()
    val benchmarkProgress: LiveData<BenchmarkEngine.BenchmarkProgress?> = _benchmarkProgress

    private val _isBenchmarking = MutableLiveData(false)
    val isBenchmarking: LiveData<Boolean> = _isBenchmarking

    val sessionCount = dao.getSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allSessions = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadLatestSession()
    }

    fun loadLatestSession() {
        viewModelScope.launch {
            val session = dao.getLatestSession()
            if (session != null) {
                try {
                    val metricsType = object : com.google.gson.reflect.TypeToken<List<BenchmarkMetrics>>() {}.type
                    val metrics: List<BenchmarkMetrics> = Gson().fromJson(session.rawJsonData, metricsType)
                    _latestMetrics.postValue(metrics)
                } catch (e: Exception) {
                    _latestMetrics.postValue(null)
                }
            }
        }
    }

    fun loadSessionById(id: Long) {
        viewModelScope.launch {
            val session = dao.getSessionById(id)
            if (session != null) {
                try {
                    val metricsType = object : com.google.gson.reflect.TypeToken<List<BenchmarkMetrics>>() {}.type
                    val metrics: List<BenchmarkMetrics> = Gson().fromJson(session.rawJsonData, metricsType)
                    _latestMetrics.postValue(metrics)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            dao.deleteSession(id)
            loadLatestSession()
        }
    }

    fun startBenchmark(
        config: BenchmarkEngine.BenchmarkConfig,
        testBitmaps: List<Bitmap>,
        testLabels: List<Int>? = null
    ) {
        if (_isBenchmarking.value == true) return

        _isBenchmarking.value = true

        viewModelScope.launch {
            val results = benchmarkEngine.runBenchmark(
                config = config,
                testBitmaps = testBitmaps,
                testLabels = testLabels
            ) { progress ->
                _benchmarkProgress.postValue(progress)
            }

            _latestMetrics.postValue(results)
            _isBenchmarking.postValue(false)
            _benchmarkProgress.postValue(null)

            // Save to Room DB
            val sessionEntity = BenchmarkSessionEntity(
                timestamp = System.currentTimeMillis(),
                deviceModel = "${deviceInfo.manufacturer} ${deviceInfo.model}",
                androidVersion = deviceInfo.androidVersion,
                cpuArch = deviceInfo.cpuArchitecture,
                executionProvider = if (config.useNNAPI) "NNAPI" else "CPU",
                numThreads = config.numThreads,
                warmupIterations = config.warmupIterations,
                benchmarkIterations = config.benchmarkIterations,
                isEmulator = deviceInfo.isEmulator,
                rawJsonData = Gson().toJson(results)
            )
            dao.insertSession(sessionEntity)
        }
    }

    fun cancelBenchmark() {
        benchmarkEngine.cancel()
        _isBenchmarking.postValue(false)
        _benchmarkProgress.postValue(null)
    }

    override fun onCleared() {
        super.onCleared()
        inferenceEngine.release()
    }
}
