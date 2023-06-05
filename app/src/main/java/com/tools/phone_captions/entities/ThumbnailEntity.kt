package com.tools.phone_captions.entities
import android.content.Context
import androidx.room.*

@Entity(tableName = "thumbnail")
data class ThumbnailEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "image_uri") val imageUri: String,
    @ColumnInfo(name = "txt_uri") val txtUri: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "folder_uri") val folderUri: String?
) {
    companion object {
        fun create(imageUri: String, txtUri: String?, description: String?, folderUri: String?): ThumbnailEntity {
            return ThumbnailEntity(imageUri = imageUri, txtUri = txtUri, description = description, folderUri = folderUri)
        }
    }
}

@Dao
interface ThumbnailDao {
    @Query("SELECT * FROM thumbnail")
    suspend fun getAll(): List<ThumbnailEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(thumbnails: List<ThumbnailEntity>)

    @Query("SELECT * FROM thumbnail WHERE folder_uri = :folderUri")
    suspend fun getFolderThumbnails(folderUri: String): List<ThumbnailEntity>

    @Query("DELETE FROM thumbnail WHERE folder_uri = :folderUri")
    suspend fun deleteByFolder(folderUri: String)

    @Query("UPDATE thumbnail SET description = :description WHERE image_uri = :imageUri")
    suspend fun updateDescription(imageUri: String, description: String)

    @Delete
    suspend fun delete(thumbnail: ThumbnailEntity)
}

@Database(entities = [ThumbnailEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun thumbnailDao(): ThumbnailDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context.applicationContext,
                AppDatabase::class.java, "AppDatabase.db")
                .build()
    }

}