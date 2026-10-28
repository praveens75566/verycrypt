package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "symmetric_keys")
data class SymmetricKeyMeta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val algorithm: String = "XChaCha20-Poly1305",
    val keyHex: String,
    val fingerprintSha256: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
