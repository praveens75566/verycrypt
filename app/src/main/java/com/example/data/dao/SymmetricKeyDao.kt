package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SymmetricKeyMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface SymmetricKeyDao {
    @Query("SELECT * FROM symmetric_keys ORDER BY createdAt DESC")
    fun getAllSymmetricKeys(): Flow<List<SymmetricKeyMeta>>

    @Query("SELECT * FROM symmetric_keys WHERE id = :id LIMIT 1")
    suspend fun getSymmetricKeyById(id: Long): SymmetricKeyMeta?

    @Query("SELECT * FROM symmetric_keys WHERE alias = :alias LIMIT 1")
    suspend fun getSymmetricKeyByAlias(alias: String): SymmetricKeyMeta?

    @Query("SELECT COUNT(*) FROM symmetric_keys")
    fun getSymmetricKeyCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymmetricKey(key: SymmetricKeyMeta): Long

    @Delete
    suspend fun deleteSymmetricKey(key: SymmetricKeyMeta)

    @Query("DELETE FROM symmetric_keys WHERE id = :id")
    suspend fun deleteSymmetricKeyById(id: Long)
}
