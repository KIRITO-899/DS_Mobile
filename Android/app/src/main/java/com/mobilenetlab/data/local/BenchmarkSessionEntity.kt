package com.mobilenetlab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "benchmark_sessions")
data class BenchmarkSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val deviceModel: String,
    val androidVersion: String,
    val cpuArch: String,
    val executionProvider: String,
    val numThreads: Int,
    val warmupIterations: Int,
    val benchmarkIterations: Int,
    val isEmulator: Boolean,
    val rawJsonData: String
)
