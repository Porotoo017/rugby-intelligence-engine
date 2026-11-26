package com.rugbypro.app.data.repository

import android.content.Context
import com.rugbypro.app.data.local.AppConfigEntity
import com.rugbypro.app.data.local.NotifiedEventEntity
import com.rugbypro.app.data.local.RugbyProDatabase
import com.rugbypro.app.data.local.SavedNewsEntity
import com.rugbypro.app.data.local.SnapshotEntity
import com.rugbypro.app.data.local.SourceHealthEntity
import com.rugbypro.app.data.local.observeAppConfig
import com.rugbypro.app.data.remote.RugbyCascadeClient
import com.rugbypro.app.data.remote.RugbyJsonParser
import com.rugbypro.app.domain.model.AppConfig
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.RugbyDashboard
import com.rugbypro.app.domain.repository.RugbyRepository
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RugbyRepositoryImpl(
    private val context: Context,
    private val db: RugbyProDatabase = RugbyProDatabase.getInstance(context),
    private val cascade: RugbyCascadeClient = RugbyCascadeClient()
) : RugbyRepository {

    private val dao = db.dao()

    override fun observeConfig(): Flow<AppConfig> = dao.observeAppConfig()

    override suspend fun updateConfig(transform: (AppConfig) -> AppConfig) = withContext(Dispatchers.IO) {
        val current = dao.getConfigEntity()?.toDomain() ?: AppConfig()
        val updated = transform(current)
        dao.saveConfigEntity(
            AppConfigEntity(
                id = 1,
                themeMode = updated.themeMode.name,
                favoriteTeam = updated.favoriteTeam,
                liveRefreshSec = updated.liveRefreshSec,
                apiTimeoutMs = updated.apiTimeoutMs,
                notifyTries = updated.notifyTries,
                notifyRedCards = updated.notifyRedCards,
                notifyFullTime = updated.notifyFullTime
            )
        )
    }

    private suspend fun readLocalPayload(): Pair<String, Boolean> {
        val cached = dao.getSnapshot()
        if (cached != null && cached.jsonPayload.isNotBlank()) {
            return cached.jsonPayload to false
        }
        val seed = context.assets.open("rugby_seed.json").bufferedReader().use { it.readText() }
        dao.saveSnapshot(SnapshotEntity(id = 1, jsonPayload = seed, updatedAtMs = System.currentTimeMillis()))
        return seed to true
    }

    override suspend fun loadInitialDashboard(): RugbyDashboard = withContext(Dispatchers.IO) {
        val (payload, _) = readLocalPayload()
        val savedIds = dao.getAllSavedNews().map { it.id }.toSet()
        val parsed = RugbyJsonParser.parseEngineSnapshot(payload, savedIds)
        val health = dao.getAllSourceHealth().map { it.toDomain() }
        val verifiedMatches = CrossVerifyMatchesUseCase.execute(parsed.matches, emptyList(), fromLocalCacheOnly = true)
        parsed.copy(
            matches = verifiedMatches,
            sourcesHealth = health,
            isOfflineFallback = true
        )
    }

    override suspend fun syncWithCascade(forceLiveRefresh: Boolean): RugbyDashboard = withContext(Dispatchers.IO) {
        val cfg = dao.getConfigEntity()?.toDomain() ?: AppConfig()
        val savedIds = dao.getAllSavedNews().map { it.id }.toSet()
        val (cachedPayload, _) = readLocalPayload()

        val result = cascade.executeCascade(
            timeoutMs = cfg.apiTimeoutMs,
            includeSnapshot = forceLiveRefresh
        )

        val finalPayload = if (!result.snapshotJson.isNullOrBlank()) {
            dao.saveSnapshot(SnapshotEntity(id = 1, jsonPayload = result.snapshotJson, updatedAtMs = System.currentTimeMillis()))
            result.snapshotJson
        } else {
            cachedPayload
        }

        if (result.healthItems.isNotEmpty()) {
            dao.upsertSourceHealth(
                result.healthItems.map {
                    SourceHealthEntity(
                        id = it.id,
                        name = it.name,
                        tier = it.tier,
                        statusLabel = it.statusLabel,
                        isHealthy = it.isHealthy,
                        latencyMs = it.latencyMs,
                        detail = it.detail,
                        checkedAtArg = it.checkedAtArg
                    )
                }
            )
        }

        val baseDash = RugbyJsonParser.parseEngineSnapshot(finalPayload, savedIds)
        val peerMatches = result.worldRugbyMatches + result.espnLiveMatches
        val anyRemoteHealthy = result.healthItems.any { it.isHealthy }
        val mergedMatches = CrossVerifyMatchesUseCase.execute(
            primary = baseDash.matches,
            secondary = peerMatches,
            fromLocalCacheOnly = !anyRemoteHealthy
        )

        val allSaved = dao.getAllSavedNews().map { it.toDomain() }
        val liveIds = baseDash.news.map { it.id }.toSet()
        val extraSaved = allSaved.filter { it.id !in liveIds }

        baseDash.copy(
            matches = mergedMatches,
            news = baseDash.news + extraSaved,
            sourcesHealth = result.healthItems,
            isOfflineFallback = !anyRemoteHealthy
        )
    }

    override suspend fun toggleSavedNews(article: NewsArticle): List<NewsArticle> = withContext(Dispatchers.IO) {
        if (dao.isNewsSaved(article.id)) {
            dao.deleteSavedNews(article.id)
        } else {
            dao.insertSavedNews(
                SavedNewsEntity(
                    id = article.id,
                    title = article.title,
                    summary = article.summary,
                    source = article.source,
                    url = article.url,
                    imageUrl = article.imageUrl,
                    category = article.category,
                    publishedLabel = article.publishedLabel,
                    savedAtMs = System.currentTimeMillis()
                )
            )
        }
        dao.getAllSavedNews().map { it.toDomain() }
    }

    override suspend fun getSavedNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        dao.getAllSavedNews().map { it.toDomain() }
    }

    override suspend fun hasNotifiedEvent(key: String): Boolean = withContext(Dispatchers.IO) {
        dao.hasNotifiedEvent(key)
    }

    override suspend fun markEventNotified(key: String) = withContext(Dispatchers.IO) {
        dao.insertNotifiedEvent(NotifiedEventEntity(eventKey = key, createdAtMs = System.currentTimeMillis()))
    }
}
