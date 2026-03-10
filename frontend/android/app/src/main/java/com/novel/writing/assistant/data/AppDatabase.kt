package com.novel.writing.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserConfig::class,
        WritingProject::class,
        GenerationHistory::class,
        ReferenceDocument::class,
        ContextInfo::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userConfigDao(): UserConfigDao
    abstract fun writingProjectDao(): WritingProjectDao
    abstract fun generationHistoryDao(): GenerationHistoryDao
    abstract fun referenceDocumentDao(): ReferenceDocumentDao
    abstract fun contextInfoDao(): ContextInfoDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_writing_assistant.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}