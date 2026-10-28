package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val extension: String,
    val internalEncryptedName: String,
    val folderCategory: String = "Default",
    val sizeBytes: Long,
    val sha256Checksum: String,
    val mimeType: String = "application/octet-stream",
    val createdAt: Long = System.currentTimeMillis()
)
