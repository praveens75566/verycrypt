package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VaultNote
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultNoteDao {
    @Query("SELECT * FROM vault_notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<VaultNote>>

    @Query("SELECT * FROM vault_notes WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun getPinnedNotes(): Flow<List<VaultNote>>

    @Query("SELECT * FROM vault_notes WHERE category = :category ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByCategory(category: String): Flow<List<VaultNote>>

    @Query("SELECT COUNT(*) FROM vault_notes")
    fun getNotesCount(): Flow<Int>

    @Query("SELECT * FROM vault_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): VaultNote?

    @Query("UPDATE vault_notes SET isPinned = CASE WHEN isPinned = 1 THEN 0 ELSE 1 END, updatedAt = :updatedAt WHERE id = :id")
    suspend fun togglePin(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: VaultNote): Long

    @Update
    suspend fun updateNote(note: VaultNote)

    @Delete
    suspend fun deleteNote(note: VaultNote)

    @Query("DELETE FROM vault_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("SELECT * FROM vault_notes")
    suspend fun getAllNotesSnapshot(): List<VaultNote>

    @Query("UPDATE vault_notes SET category = :newCategory WHERE id IN (:ids)")
    suspend fun updateCategoryForNotes(ids: List<Long>, newCategory: String)
}
