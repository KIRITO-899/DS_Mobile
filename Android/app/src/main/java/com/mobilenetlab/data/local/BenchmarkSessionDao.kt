package com.mobilenetlab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BenchmarkSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BenchmarkSessionEntity): Long

    @Query("SELECT * FROM benchmark_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<BenchmarkSessionEntity>>

    @Query("SELECT * FROM benchmark_sessions ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSession(): BenchmarkSessionEntity?

    @Query("SELECT * FROM benchmark_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): BenchmarkSessionEntity?

    @Query("DELETE FROM benchmark_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT COUNT(*) FROM benchmark_sessions")
    fun getSessionCount(): Flow<Int>
}
