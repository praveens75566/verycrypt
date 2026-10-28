package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.KeyPairMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyPairDao {
    @Query("SELECT * FROM key_pairs ORDER BY createdAt DESC")
    fun getAllKeyPairs(): Flow<List<KeyPairMeta>>

    @Query("SELECT * FROM key_pairs WHERE alias = :alias LIMIT 1")
    suspend fun getKeyPairByAlias(alias: String): KeyPairMeta?

    @Query("SELECT COUNT(*) FROM key_pairs")
    fun getKeyPairCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyPair(keyPair: KeyPairMeta)

    @Delete
    suspend fun deleteKeyPair(keyPair: KeyPairMeta)

    @Query("DELETE FROM key_pairs WHERE alias = :alias")
    suspend fun deleteKeyPairByAlias(alias: String)
}
