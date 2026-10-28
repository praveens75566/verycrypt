package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AuditEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAuditEvents(): Flow<List<AuditEvent>>

    @Query("SELECT COUNT(*) FROM audit_events")
    fun getAuditEventCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AuditEvent): Long

    @Query("DELETE FROM audit_events")
    suspend fun clearAuditLog()
}
