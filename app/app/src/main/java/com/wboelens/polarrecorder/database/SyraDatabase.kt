package com.wboelens.polarrecorder.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [ResearchSession::class, SurveyResponse::class],
    version = 1,
    exportSchema = false,
)
abstract class SyraDatabase : RoomDatabase() {
    abstract fun sessionDao(): ResearchSessionDao
    abstract fun surveyResponseDao(): SurveyResponseDao

    companion object {
        @Volatile private var INSTANCE: SyraDatabase? = null

        fun getInstance(context: Context, passphrase: ByteArray): SyraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): SyraDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                SyraDatabase::class.java,
                "syra_local.db",
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
