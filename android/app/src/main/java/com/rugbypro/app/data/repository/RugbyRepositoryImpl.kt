package com.rugbypro.app.data.repository

import android.content.Context
import com.rugbypro.app.data.local.AppConfigEntity
import com.rugbypro.app.data.local.NotifiedEventEntity
import com.rugbypro.app.data.local.RugbyProDatabase
import com.rugbypro.app.data.local.SavedNewsEntity
import java.io.File
import com.rugbypro.app.data.local.SourceHealthEntity
import com.rugbypro.app.data.local.observeAppConfig
import com.rugbypro.app.data.remote.RugbyCascadeClient
import com.rugbypro.app.data.remote.RugbyJsonParser
import com.rugbypro.app.domain.model.AppConfig
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.RugbyDashboard
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.repository.RugbyRepository
import com.rugbypro.app.domain.usecase.CrossVerifyMatchesUseCase
import com.rugbypro.app.live.PreMatchAlarmScheduler
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
                notifyFullTime = updated.notifyFullTime,
                onboardingCompleted = updated.onboardingCompleted,
                liveTutorialSeen = updated.liveTutorialSeen
            )
        )
    }

    private fun snapshotCacheFile(): File = File(context.filesDir, "rugby_snapshot_cache.json")

    private fun writeSnapshotFile(payload: String) {
        if (payload.isBlank()) return
        runCatching {
            val target = snapshotCacheFile()
            val tmp = File(context.filesDir, "rugby_snapshot_cache.json.tmp")
            tmp.writeText(payload, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(payload, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private suspend fun readLocalPayload(): Pair<String, Boolean> {
        val cachedFile = snapshotCacheFile()
        if (cachedFile.exists() && cachedFile.length() > 64L) {
            val txt = runCatching { cachedFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!txt.isNullOrBlank()) {
                return txt to false
            }
        }
        val seed = context.assets.open("rugby_seed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        writeSnapshotFile(seed)
        return seed to true
    }

    override suspend fun loadInitialDashboard(): RugbyDashboard = withContext(Dispatchers.IO) {
        val (payload, _) = readLocalPayload()
        val savedIds = dao.getAllSavedNews().map { it.id }.toSet()
        val parsed = RugbyJsonParser.parseEngineSnapshot(payload, savedIds)
        val verifiedMatches = CrossVerifyMatchesUseCase.execute(parsed.matches, emptyList(), fromLocalCacheOnly = true)
        val withKits = RugbyJsonParser.attachTeamKitsToMatches(verifiedMatches, parsed.teams)
        runCatching { PreMatchAlarmScheduler.scheduleUpcoming(context, withKits) }
        parsed.copy(
            matches = withKits,
            sourcesHealth = emptyList(),
            isOfflineFallback = true
        )
    }

    override suspend fun syncWithCascade(forceLiveRefresh: Boolean, forcePreMatchId: String?): RugbyDashboard = withContext(Dispatchers.IO) {
        val cfg = dao.getConfigEntity()?.toDomain() ?: AppConfig()
        val savedIds = dao.getAllSavedNews().map { it.id }.toSet()
        val (cachedPayload, _) = readLocalPayload()

        val result = cascade.executeCascade(
            timeoutMs = cfg.apiTimeoutMs,
            includeSnapshot = forceLiveRefresh
        )

        val finalPayload = if (!result.snapshotJson.isNullOrBlank()) {
            writeSnapshotFile(result.snapshotJson)
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
        val anyRemoteHealthy = result.healthItems.any { it.isHealthy }
        val step1 = CrossVerifyMatchesUseCase.execute(
            primary = baseDash.matches,
            secondary = result.worldRugbyMatches,
            fromLocalCacheOnly = !anyRemoteHealthy
        )
        val mergedMatches = CrossVerifyMatchesUseCase.execute(
            primary = step1,
            secondary = result.secondaryLiveMatches,
            fromLocalCacheOnly = !anyRemoteHealthy
        )
        val withOfficialDetails = if (anyRemoteHealthy) {
            runCatching {
                cascade.enrichMatchesWithOfficialDetails(mergedMatches, cfg.apiTimeoutMs, forcePreMatchId)
            }.getOrDefault(mergedMatches)
        } else {
            mergedMatches
        }
        val withKits = RugbyJsonParser.attachTeamKitsToMatches(withOfficialDetails, baseDash.teams)
        val withWeather = if (anyRemoteHealthy) {
            runCatching {
                cascade.enrichMatchesWithWeather(withKits, cfg.apiTimeoutMs, bypassCacheForMatchId = forcePreMatchId)
            }.getOrDefault(withKits)
        } else {
            withKits
        }

        runCatching { PreMatchAlarmScheduler.scheduleUpcoming(context, withWeather) }

        val allSaved = dao.getAllSavedNews().map { it.toDomain() }
        val savedIdsSet = allSaved.map { it.id }.toSet()
        val combinedNews = mutableListOf<NewsArticle>()
        val seenNewsKeys = mutableSetOf<String>()
        val seenNewsIds = mutableSetOf<String>()
        for (art in result.liveNews + baseDash.news + allSaved) {
            val normTitle = art.title.lowercase().replace(Regex("[^a-z0-9]"), "").take(64)
            val effectiveId = art.id.ifBlank { "art_${combinedNews.size}_${normTitle.take(24)}" }
            if (effectiveId in seenNewsIds) continue
            if (normTitle.isNotBlank() && normTitle in seenNewsKeys) continue
            seenNewsIds.add(effectiveId)
            if (normTitle.isNotBlank()) seenNewsKeys.add(normTitle)
            combinedNews.add(art.copy(id = effectiveId, savedOffline = effectiveId in savedIdsSet || art.savedOffline))
        }

        val uniqueMatches = withWeather.distinctBy { it.id.ifBlank { "${it.homeName}_${it.awayName}_${it.timestampSec}" } }

        baseDash.copy(
            matches = uniqueMatches,
            news = combinedNews,
            sourcesHealth = emptyList(),
            isOfflineFallback = !anyRemoteHealthy
        )
    }

    override suspend fun refreshMatchDetail(match: RugbyMatch): RugbyMatch = withContext(Dispatchers.IO) {
        val cfg = dao.getConfigEntity()?.toDomain() ?: AppConfig()
        val (cachedPayload, _) = readLocalPayload()
        val savedIds = dao.getAllSavedNews().map { it.id }.toSet()
        val baseDash = runCatching { RugbyJsonParser.parseEngineSnapshot(cachedPayload, savedIds) }.getOrNull()
        val enriched = runCatching {
            cascade.fetchSingleMatchLiveDetail(match, cfg.apiTimeoutMs)
        }.getOrDefault(match)
        val withKits = RugbyJsonParser.attachTeamKitsToMatches(listOf(enriched), baseDash?.teams.orEmpty()).first()
        runCatching {
            cascade.enrichMatchesWithWeather(listOf(withKits), cfg.apiTimeoutMs, bypassCacheForMatchId = withKits.id).first()
        }.getOrDefault(withKits)
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
