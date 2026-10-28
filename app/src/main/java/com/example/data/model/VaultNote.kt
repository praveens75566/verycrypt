package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_notes")
data class VaultNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val encryptedContent: String, // Armored AES-256-GCM or XChaCha20-Poly1305 ciphertext of main text
    val category: String = "General",
    val isPinned: Boolean = false,
    val colorIndex: Int = 0, // 0: Slate, 1: Emerald, 2: Amber, 3: Blue, 4: Rose, 5: Purple
    val tags: String = "", // Comma-separated tags e.g. "seed,btc,hardware"
    val algorithm: String = "AES-256-GCM", // "AES-256-GCM" or "XChaCha20-Poly1305"
    val keyAlias: String = "MASTER_VAULT", // "MASTER_VAULT" or custom alias
    val encryptedSecretFields: String = "", // Armored ciphertext of JSON secret fields list
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
