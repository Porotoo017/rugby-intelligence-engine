package com.rugbypro.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rugbypro.app.domain.model.AppConfig
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.SourceHealthItem
import com.rugbypro.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "cached_snapshot")
data class SnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val jsonPayload: String,
    val updatedAtMs: Long
)

@Entity(tableName = "saved_news")
data class SavedNewsEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val imageUrl: String,
    val category: String,
    val publishedLabel: String,
    val savedAtMs: Long
) {
    fun toDomain() = NewsArticle(
        id = id,
        title = title,
        summary = summary,
        source = source,
        url = url,
        imageUrl = imageUrl,
        category = category,
        publishedLabel = publishedLabel,
        savedOffline = true
    )
}

@Entity(tableName = "source_health")
data class SourceHealthEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tier: Int,
    val statusLabel: String,
    val isHealthy: Boolean,
    val latencyMs: Long,
    val detail: String,
    val checkedAtArg: String
) {
    fun toDomain() = SourceHealthItem(
        id = id,
        name = name,
        tier = tier,
        statusLabel = statusLabel,
        isHealthy = isHealthy,
        latencyMs = latencyMs,
        detail = detail,
        checkedAtArg = checkedAtArg
    )
}

@Entity(tableName = "notified_events")
data class NotifiedEventEntity(
    @PrimaryKey val eventKey: String,
    val createdAtMs: Long
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = ThemeMode.DARK.name,
    val favoriteTeam: String = "Argentina",
    val liveRefreshSec: Int = 30,
    val apiTimeoutMs: Long = 10000L,
    val notifyTries: Boolean = true,
    val notifyRedCards: Boolean = true,
    val notifyFullTime: Boolean = true
) {
    fun toDomain() = AppConfig(
        themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.DARK),
        favoriteTeam = favoriteTeam,
        liveRefreshSec = liveRefreshSec,
        apiTimeoutMs = apiTimeoutMs,
        notifyTries = notifyTries,
        notifyRedCards = notifyRedCards,
        notifyFullTime = notifyFullTime
    )
}

@Dao
interface RugbyDao {
    @Query("SELECT * FROM cached_snapshot WHERE id = 1")
    suspend fun getSnapshot(): SnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(entity: SnapshotEntity)

    @Query("SELECT * FROM saved_news ORDER BY savedAtMs DESC")
    suspend fun getAllSavedNews(): List<SavedNewsEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_news WHERE id = :id)")
    suspend fun isNewsSaved(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedNews(entity: SavedNewsEntity)

    @Query("DELETE FROM saved_news WHERE id = :id")
    suspend fun deleteSavedNews(id: String)

    @Query("SELECT * FROM source_health ORDER BY tier ASC, name ASC")
    suspend fun getAllSourceHealth(): List<SourceHealthEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceHealth(items: List<SourceHealthEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM notified_events WHERE eventKey = :key)")
    suspend fun hasNotifiedEvent(key: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifiedEvent(entity: NotifiedEventEntity)

    @Query("SELECT * FROM app_config WHERE id = 1")
    fun observeConfigEntity(): Flow<AppConfigEntity?>

    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun getConfigEntity(): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfigEntity(entity: AppConfigEntity)
}

@Database(
    entities = [
        SnapshotEntity::class,
        SavedNewsEntity::class,
        SourceHealthEntity::class,
        NotifiedEventEntity::class,
        AppConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RugbyProDatabase : RoomDatabase() {
    abstract fun dao(): RugbyDao

    companion object {
        @Volatile
        private var INSTANCE: RugbyProDatabase? = null

        fun getInstance(context: Context): RugbyProDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RugbyProDatabase::class.java,
                    "rugby_pro.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

fun RugbyDao.observeAppConfig(): Flow<AppConfig> =
    observeConfigEntity().map { it?.toDomain() ?: AppConfig() }
