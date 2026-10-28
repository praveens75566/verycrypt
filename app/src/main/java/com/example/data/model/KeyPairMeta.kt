package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "key_pairs")
data class KeyPairMeta(
    @PrimaryKey val alias: String,
    val algorithm: String = "ECDSA",
    val curve: String = "secp256r1 (NIST P-256)",
    val publicKeyPem: String,
    val fingerprintSha256: String,
    val isKeystoreBacked: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
