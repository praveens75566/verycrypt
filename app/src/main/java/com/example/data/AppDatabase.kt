package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AuditDao
import com.example.data.dao.KeyPairDao
import com.example.data.dao.SymmetricKeyDao
import com.example.data.dao.VaultDao
import com.example.data.dao.VaultNoteDao
import com.example.data.model.AuditEvent
import com.example.data.model.KeyPairMeta
import com.example.data.model.SymmetricKeyMeta
import com.example.data.model.VaultItem
import com.example.data.model.VaultNote

@Database(
    entities = [
        AuditEvent::class,
        VaultItem::class,
        KeyPairMeta::class,
        VaultNote::class,
        SymmetricKeyMeta::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
    abstract fun vaultDao(): VaultDao
    abstract fun keyPairDao(): KeyPairDao
    abstract fun vaultNoteDao(): VaultNoteDao
    abstract fun symmetricKeyDao(): SymmetricKeyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vericrypt_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
