package com.rugbypro.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rugbypro.app.data.repository.RugbyRepositoryImpl
import com.rugbypro.app.domain.model.AppConfig
import com.rugbypro.app.domain.model.AppSection
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.PlayerComparisonResult
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.RugbyDashboard
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamComparisonResult
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.usecase.ComparePlayersUseCase
import com.rugbypro.app.domain.usecase.CompareTeamsUseCase
import com.rugbypro.app.domain.usecase.FilterMatchesUseCase
import com.rugbypro.app.live.LiveMatchTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RugbyUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val currentSection: AppSection = AppSection.INICIO,
    val dashboard: RugbyDashboard? = null,
    val config: AppConfig = AppConfig(),
    val fixtureGroup: String = "todos",
    val fixtureStatus: MatchStatus? = null,
    val fixtureQuery: String = "",
    val selectedStandingId: String = "",
    val teamQuery: String = "",
    val teamGroupFilter: String = "todos",
    val selectedTeam: TeamProfile? = null,
    val compareTeamA: TeamProfile? = null,
    val compareTeamB: TeamProfile? = null,
    val teamComparison: TeamComparisonResult? = null,
    val comparePlayerA: Pair<String, PlayerProfile>? = null,
    val comparePlayerB: Pair<String, PlayerProfile>? = null,
    val playerComparison: PlayerComparisonResult? = null,
    val newsCategory: String = "Todas",
    val showSavedNewsOnly: Boolean = false,
    val selectedMatchDetail: RugbyMatch? = null,
    val snackbarMessage: String? = null
) {
    val filteredMatches: List<RugbyMatch>
        get() = FilterMatchesUseCase.execute(
            matches = dashboard?.matches.orEmpty(),
            groupFilter = fixtureGroup,
            statusFilter = fixtureStatus,
            query = fixtureQuery
        )

    val liveMatches: List<RugbyMatch>
        get() = dashboard?.matches?.filter { it.status == MatchStatus.LIVE }.orEmpty()
}

class RugbyViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = RugbyRepositoryImpl(app)
    private val tracker = LiveMatchTracker(app, repository)

    private val _uiState = MutableStateFlow(RugbyUiState())
    val uiState: StateFlow<RugbyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeConfig().collect { cfg ->
                _uiState.update { it.copy(config = cfg) }
            }
        }
        viewModelScope.launch {
            val initial = repository.loadInitialDashboard()
            applyDashboard(initial, isLoading = false)
            refreshNow(forceSnapshot = true)
            tracker.start(
                scope = viewModelScope,
                getConfig = { _uiState.value.config },
                getCurrentDashboard = { _uiState.value.dashboard },
                onDashboardUpdated = { updated -> applyDashboard(updated, isLoading = false) }
            )
        }
    }

    private fun applyDashboard(dash: RugbyDashboard, isLoading: Boolean) {
        _uiState.update { cur ->
            val standingId = cur.selectedStandingId.ifBlank { dash.standings.firstOrNull()?.id.orEmpty() }
            val teamsWithRoster = dash.teams.filter { it.roster.isNotEmpty() }
            val teamA = cur.compareTeamA ?: dash.teams.firstOrNull { it.name.contains("Argentina", true) } ?: dash.teams.firstOrNull()
            val teamB = cur.compareTeamB ?: dash.teams.firstOrNull { it.name != teamA?.name && it.worldRank != null } ?: dash.teams.getOrNull(1)
            val compTeam = if (teamA != null && teamB != null) {
                CompareTeamsUseCase.execute(teamA, teamB, dash.matches)
            } else null

            val pA = cur.comparePlayerA ?: teamsWithRoster.firstOrNull()?.let { t -> t.roster.firstOrNull()?.let { t.name to it } }
            val pB = cur.comparePlayerB ?: teamsWithRoster.drop(1).firstOrNull()?.let { t -> t.roster.firstOrNull()?.let { t.name to it } }
            val compPlayer = if (pA != null && pB != null) {
                ComparePlayersUseCase.execute(pA.first, pA.second, pB.first, pB.second)
            } else null

            cur.copy(
                isLoading = isLoading,
                dashboard = dash,
                selectedStandingId = standingId,
                compareTeamA = teamA,
                compareTeamB = teamB,
                teamComparison = compTeam,
                comparePlayerA = pA,
                comparePlayerB = pB,
                playerComparison = compPlayer
            )
        }
    }

    fun selectSection(section: AppSection) {
        _uiState.update { it.copy(currentSection = section) }
    }

    fun refreshNow(forceSnapshot: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            runCatching {
                repository.syncWithCascade(forceLiveRefresh = forceSnapshot)
            }.onSuccess { updated ->
                applyDashboard(updated, isLoading = false)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        snackbarMessage = if (updated.isOfflineFallback) {
                            "Modo offline: usando caché local"
                        } else {
                            "Fuentes verificadas actualizadas"
                        }
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isSyncing = false, snackbarMessage = "Sin conexión: datos locales activos") }
            }
        }
    }

    fun setFixtureFilter(group: String? = null, status: MatchStatus? = _uiState.value.fixtureStatus, query: String? = null) {
        _uiState.update {
            it.copy(
                fixtureGroup = group ?: it.fixtureGroup,
                fixtureStatus = status,
                fixtureQuery = query ?: it.fixtureQuery
            )
        }
    }

    fun selectStanding(id: String) {
        _uiState.update { it.copy(selectedStandingId = id) }
    }

    fun setTeamFilter(group: String? = null, query: String? = null) {
        _uiState.update {
            it.copy(
                teamGroupFilter = group ?: it.teamGroupFilter,
                teamQuery = query ?: it.teamQuery
            )
        }
    }

    fun openTeamDetail(team: TeamProfile?) {
        _uiState.update { it.copy(selectedTeam = team) }
    }

    fun openMatchDetail(match: RugbyMatch?) {
        _uiState.update { it.copy(selectedMatchDetail = match) }
    }

    fun cycleCompareTeams(isLeft: Boolean) {
        val teams = _uiState.value.dashboard?.teams.orEmpty()
        if (teams.size < 2) return
        val cur = if (isLeft) _uiState.value.compareTeamA else _uiState.value.compareTeamB
        val idx = teams.indexOfFirst { it.key == cur?.key }
        val next = teams[(idx + 1) % teams.size]
        _uiState.update { state ->
            val a = if (isLeft) next else state.compareTeamA ?: teams[0]
            val b = if (!isLeft) next else state.compareTeamB ?: teams[1]
            state.copy(
                compareTeamA = a,
                compareTeamB = b,
                teamComparison = CompareTeamsUseCase.execute(a, b, state.dashboard?.matches.orEmpty())
            )
        }
    }

    fun cycleComparePlayers(isLeft: Boolean) {
        val pool = _uiState.value.dashboard?.teams.orEmpty()
            .flatMap { t -> t.roster.map { p -> t.name to p } }
        if (pool.size < 2) return
        val cur = if (isLeft) _uiState.value.comparePlayerA else _uiState.value.comparePlayerB
        val idx = pool.indexOfFirst { it.second.name == cur?.second?.name }
        val next = pool[(idx + 1) % pool.size]
        _uiState.update { state ->
            val a = if (isLeft) next else state.comparePlayerA ?: pool[0]
            val b = if (!isLeft) next else state.comparePlayerB ?: pool[1]
            state.copy(
                comparePlayerA = a,
                comparePlayerB = b,
                playerComparison = ComparePlayersUseCase.execute(a.first, a.second, b.first, b.second)
            )
        }
    }

    fun setNewsFilter(category: String? = null, savedOnly: Boolean? = null) {
        _uiState.update {
            it.copy(
                newsCategory = category ?: it.newsCategory,
                showSavedNewsOnly = savedOnly ?: it.showSavedNewsOnly
            )
        }
    }

    fun toggleSavedNews(article: NewsArticle) {
        viewModelScope.launch {
            val saved = repository.toggleSavedNews(article)
            val savedIds = saved.map { it.id }.toSet()
            _uiState.update { state ->
                val dash = state.dashboard ?: return@update state
                state.copy(
                    dashboard = dash.copy(
                        news = dash.news.map { it.copy(savedOffline = it.id in savedIds) }
                    ),
                    snackbarMessage = if (article.id in savedIds) "Noticia guardada para leer offline" else "Quitada de guardadas"
                )
            }
        }
    }

    fun updateConfig(transform: (AppConfig) -> AppConfig) {
        viewModelScope.launch {
            repository.updateConfig(transform)
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        tracker.stop()
        super.onCleared()
    }
}
