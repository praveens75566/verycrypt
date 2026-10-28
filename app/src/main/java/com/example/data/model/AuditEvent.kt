package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuditAction {
    HASH,
    HASH_COMPARE,
    ENCRYPT,
    DECRYPT,
    KEY_GENERATE,
    SIGN,
    VERIFY,
    VAULT_IMPORT,
    VAULT_EXPORT,
    VAULT_DELETE,
    APP_LOCK_LOGIN
}

enum class AuditStatus {
    SUCCESS,
    FAILED,
    TAMPER_DETECTED,
    VERIFIED_VALID,
    VERIFIED_INVALID
}

@Entity(tableName = "audit_events")
data class AuditEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: AuditAction,
    val itemName: String,
    val status: AuditStatus,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
