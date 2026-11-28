package com.rugbypro.app.domain.repository

import com.rugbypro.app.domain.model.AppConfig
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.RugbyDashboard
import kotlinx.coroutines.flow.Flow

interface RugbyRepository {
    fun observeConfig(): Flow<AppConfig>
    suspend fun updateConfig(transform: (AppConfig) -> AppConfig)
    suspend fun loadInitialDashboard(): RugbyDashboard
    suspend fun syncWithCascade(forceLiveRefresh: Boolean = true, forcePreMatchId: String? = null): RugbyDashboard
    suspend fun syncLivePulseOnly(currentDashboard: RugbyDashboard?): RugbyDashboard
    suspend fun refreshMatchDetail(match: com.rugbypro.app.domain.model.RugbyMatch): com.rugbypro.app.domain.model.RugbyMatch
    suspend fun toggleSavedNews(article: NewsArticle): List<NewsArticle>
    suspend fun getSavedNews(): List<NewsArticle>
    suspend fun hasNotifiedEvent(key: String): Boolean
    suspend fun markEventNotified(key: String)
}
