package com.scrollmeter.app.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.scrollmeter.app.data.database.entities.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UsageSessionEntity): Long

    @Update
    suspend fun updateSession(session: UsageSessionEntity)

    @Query("SELECT * FROM usage_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): UsageSessionEntity?

    @Query("SELECT * FROM usage_sessions WHERE is_closed = 0")
    suspend fun getOpenSessions(): List<UsageSessionEntity>

    @Query(
        """
        SELECT * FROM usage_sessions
        WHERE local_date = :localDate
        ORDER BY start_timestamp DESC
        """
    )
    fun observeSessionsForDate(localDate: String): Flow<List<UsageSessionEntity>>

    @Query(
        """
        SELECT * FROM usage_sessions
        WHERE local_date BETWEEN :startDate AND :endDate
        ORDER BY start_timestamp DESC
        """
    )
    fun observeSessionsInRange(startDate: String, endDate: String): Flow<List<UsageSessionEntity>>

    @Query("SELECT * FROM usage_sessions ORDER BY start_timestamp ASC")
    suspend fun getAllSessionsSnapshot(): List<UsageSessionEntity>

    @Query("DELETE FROM usage_sessions WHERE local_date = :localDate")
    suspend fun deleteSessionsByDate(localDate: String)

    @Query("DELETE FROM usage_sessions WHERE local_date BETWEEN :startDate AND :endDate")
    suspend fun deleteSessionsInRange(startDate: String, endDate: String)

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAllSessions()
}
