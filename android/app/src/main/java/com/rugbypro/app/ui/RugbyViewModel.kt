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
import com.rugbypro.app.domain.model.PlayerSearchEntry
import com.rugbypro.app.domain.model.RugbyDashboard
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamComparisonResult
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.usecase.ComparePlayersUseCase
import com.rugbypro.app.domain.usecase.CompareTeamsUseCase
import com.rugbypro.app.domain.usecase.FilterLiveAndTodayMatchesUseCase
import com.rugbypro.app.domain.usecase.FilterMatchesUseCase
import com.rugbypro.app.domain.usecase.SearchTeamsAndPlayersUseCase
import com.rugbypro.app.live.LiveMatchTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RugbyUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val currentSection: AppSection = AppSection.INICIO,
    val config: AppConfig = AppConfig(),
    val dashboard: RugbyDashboard? = null,
    val fixtureGroup: String = "todos",
    val fixtureStatus: MatchStatus? = null,
    val fixtureQuery: String = "",
    val fixtureDateQuery: String = "",
    val selectedStandingId: String = "",
    val teamQuery: String = "",
    val teamGroupFilter: String = "todos",
    val selectedTeam: TeamProfile? = null,
    val selectedPlayer: PlayerSearchEntry? = null,
    // Comparación con buscador multi-filtro
    val compareMode: String = "teams", // "teams" | "players"
    val compareQuery: String = "",
    val compareCountryFilter: String = "Todos",
    val compareTeamFilter: String = "Todos",
    val comparePositionFilter: String = "Todas",
    val compareTeamA: TeamProfile? = null,
    val compareTeamB: TeamProfile? = null,
    val teamComparison: TeamComparisonResult? = null,
    val comparePlayerA: PlayerSearchEntry? = null,
    val comparePlayerB: PlayerSearchEntry? = null,
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
            query = fixtureQuery,
            dateQuery = fixtureDateQuery
        )

    // Estrictamente SOLO partidos en vivo ahora (nunca finalizados)
    val liveMatches: List<RugbyMatch>
        get() = FilterLiveAndTodayMatchesUseCase.liveNow(dashboard?.matches.orEmpty())

    // Estrictamente SOLO partidos próximos a jugarse hoy (nunca finalizados)
    val upcomingTodayMatches: List<RugbyMatch>
        get() = FilterLiveAndTodayMatchesUseCase.upcomingToday(dashboard?.matches.orEmpty())

    val playerSearchResults: List<PlayerSearchEntry>
        get() = SearchTeamsAndPlayersUseCase.searchPlayers(
            teams = dashboard?.teams.orEmpty(),
            query = compareQuery,
            countryFilter = compareCountryFilter,
            teamFilter = compareTeamFilter,
            positionFilter = comparePositionFilter
        )

    val teamSearchResults: List<TeamProfile>
        get() = SearchTeamsAndPlayersUseCase.searchTeams(
            teams = dashboard?.teams.orEmpty(),
            query = compareQuery,
            countryOrGroupFilter = compareCountryFilter
        )
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

            val allEntries = teamsWithRoster.flatMap { t ->
                t.roster.map { p ->
                    PlayerSearchEntry(t.key, t.name, t.crestUrl, p.nationalityFlagUrl ?: t.flagUrl, p, t.homeKit ?: t.awayKit)
                }
            }
            val pA = cur.comparePlayerA ?: allEntries.firstOrNull { it.teamName.contains("Argentina", true) } ?: allEntries.firstOrNull()
            val pB = cur.comparePlayerB ?: allEntries.firstOrNull { it.teamName != pA?.teamName } ?: allEntries.getOrNull(1)
            val compPlayer = if (pA != null && pB != null) {
                ComparePlayersUseCase.execute(pA.teamName, pA.teamFlagUrl, pA.player, pB.teamName, pB.teamFlagUrl, pB.player)
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
            _uiState.update { it.copy(isSyncing = true, syncError = null) }
            runCatching {
                repository.syncWithCascade(forceLiveRefresh = forceSnapshot)
            }.onSuccess { updated ->
                applyDashboard(updated, isLoading = false)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncError = null,
                        snackbarMessage = if (updated.isOfflineFallback) {
                            "Sin conexión. Mostrando últimos datos guardados."
                        } else {
                            "Marcadores y planteles actualizados"
                        }
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncError = "No se pudo actualizar en este momento. Verificá tu conexión e intentá nuevamente.",
                        snackbarMessage = "Sin conexión. Mostrando últimos datos guardados."
                    )
                }
            }
        }
    }

    fun completeOnboarding(favoriteTeam: String) {
        viewModelScope.launch {
            repository.updateConfig {
                it.copy(
                    favoriteTeam = favoriteTeam.ifBlank { it.favoriteTeam },
                    onboardingCompleted = true
                )
            }
        }
    }

    fun replayOnboarding() {
        viewModelScope.launch {
            repository.updateConfig { it.copy(onboardingCompleted = false) }
        }
    }

    fun dismissLiveTutorial() {
        viewModelScope.launch {
            repository.updateConfig { it.copy(liveTutorialSeen = true) }
        }
    }

    fun setFixtureFilter(
        group: String? = null,
        status: MatchStatus? = _uiState.value.fixtureStatus,
        query: String? = null,
        dateQuery: String? = null
    ) {
        _uiState.update {
            it.copy(
                fixtureGroup = group ?: it.fixtureGroup,
                fixtureStatus = status,
                fixtureQuery = query ?: it.fixtureQuery,
                fixtureDateQuery = dateQuery ?: it.fixtureDateQuery
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

    fun openPlayerDetail(entry: PlayerSearchEntry?) {
        _uiState.update { it.copy(selectedPlayer = entry) }
    }

    fun openPlayerFromTeam(team: TeamProfile, player: PlayerProfile) {
        _uiState.update {
            it.copy(
                selectedPlayer = PlayerSearchEntry(
                    teamKey = team.key,
                    teamName = team.name,
                    teamCrestUrl = team.crestUrl,
                    teamFlagUrl = player.nationalityFlagUrl ?: team.flagUrl,
                    player = player,
                    teamKit = team.homeKit ?: team.awayKit
                )
            )
        }
    }

    fun openPlayerFromMatch(match: RugbyMatch, isHome: Boolean, player: PlayerProfile) {
        val tName = if (isHome) match.homeName else match.awayName
        val tCrest = if (isHome) match.homeCrestUrl else match.awayCrestUrl
        val tFlag = player.nationalityFlagUrl ?: if (isHome) match.homeFlagUrl else match.awayFlagUrl
        val kit = if (isHome) match.homeKit else match.awayKit
        _uiState.update {
            it.copy(
                selectedPlayer = PlayerSearchEntry(
                    teamKey = tName.lowercase(),
                    teamName = tName,
                    teamCrestUrl = tCrest,
                    teamFlagUrl = tFlag,
                    player = player,
                    teamKit = kit
                )
            )
        }
    }

    fun openMatchDetail(match: RugbyMatch?) {
        _uiState.update { it.copy(selectedMatchDetail = match) }
    }

    fun setCompareMode(mode: String) {
        _uiState.update { it.copy(compareMode = mode) }
    }

    fun setCompareFilters(
        query: String? = null,
        country: String? = null,
        team: String? = null,
        position: String? = null
    ) {
        _uiState.update {
            it.copy(
                compareQuery = query ?: it.compareQuery,
                compareCountryFilter = country ?: it.compareCountryFilter,
                compareTeamFilter = team ?: it.compareTeamFilter,
                comparePositionFilter = position ?: it.comparePositionFilter
            )
        }
    }

    fun assignCompareTeam(isLeft: Boolean, team: TeamProfile) {
        _uiState.update { state ->
            val a = if (isLeft) team else state.compareTeamA ?: team
            val b = if (!isLeft) team else state.compareTeamB ?: team
            state.copy(
                compareTeamA = a,
                compareTeamB = b,
                teamComparison = CompareTeamsUseCase.execute(a, b, state.dashboard?.matches.orEmpty())
            )
        }
    }

    fun assignComparePlayer(isLeft: Boolean, entry: PlayerSearchEntry) {
        _uiState.update { state ->
            val a = if (isLeft) entry else state.comparePlayerA ?: entry
            val b = if (!isLeft) entry else state.comparePlayerB ?: entry
            state.copy(
                comparePlayerA = a,
                comparePlayerB = b,
                playerComparison = ComparePlayersUseCase.execute(
                    a.teamName, a.teamFlagUrl, a.player,
                    b.teamName, b.teamFlagUrl, b.player
                )
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
                    snackbarMessage = if (article.id in savedIds) "Noticia guardada para leer sin conexión" else "Quitada de guardadas"
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
