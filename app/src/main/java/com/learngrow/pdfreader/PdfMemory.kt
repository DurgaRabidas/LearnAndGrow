package com.learngrow.pdfreader

import android.content.Context
import androidx.room.*

@Entity(tableName = "pdf_memory")
data class PdfMemory(
    @PrimaryKey val uriString: String,
    val fileName: String,
    val folderPath: String,
    val fileSizeBytes: Long,
    val lastPage: Int,
    val totalPages: Int,
    val lastZoom: Float = 1.0f,
    val lastAccessed: Long = System.currentTimeMillis()
)

@Dao
interface PdfMemoryDao {
    @Query("SELECT * FROM pdf_memory ORDER BY lastAccessed DESC")
    suspend fun getAllRecords(): List<PdfMemory>

    @Query("SELECT * FROM pdf_memory WHERE uriString = :uri LIMIT 1")
    suspend fun getRecord(uri: String): PdfMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(record: PdfMemory)

    @Query("DELETE FROM pdf_memory WHERE uriString = :uri")
    suspend fun forgetProgressOnly(uri: String)

    @Query("DELETE FROM pdf_memory")
    suspend fun clearAllProgress()
}

@Database(entities = [PdfMemory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfMemoryDao(): PdfMemoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learngrow_memory.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
