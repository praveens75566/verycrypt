package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun getAllVaultItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE folderCategory = :category ORDER BY createdAt DESC")
    fun getVaultItemsByCategory(category: String): Flow<List<VaultItem>>

    @Query("SELECT DISTINCT folderCategory FROM vault_items ORDER BY folderCategory ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM vault_items")
    fun getVaultItemCount(): Flow<Int>

    @Query("SELECT SUM(sizeBytes) FROM vault_items")
    fun getTotalVaultSizeBytes(): Flow<Long?>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: Long): VaultItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem): Long

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: Long)

    @Query("SELECT * FROM vault_items")
    suspend fun getAllVaultItemsSnapshot(): List<VaultItem>

    @Query("UPDATE vault_items SET folderCategory = :newCategory WHERE id IN (:ids)")
    suspend fun updateCategoryForItems(ids: List<Long>, newCategory: String)
}
