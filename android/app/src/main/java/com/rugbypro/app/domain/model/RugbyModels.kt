package com.rugbypro.app.domain.model

enum class MatchStatus(val label: String) {
    LIVE("EN VIVO"),
    SCHEDULED("PROGRAMADO"),
    FINISHED("FINALIZADO")
}

enum class VerificationStatus(val label: String) {
    VERIFIED("VERIFICADO"),
    PENDING_VERIFICATION("Pendiente de verificación"),
    LOCAL_CACHE("Caché local")
}

enum class MatchEventType(val label: String) {
    TRY("Try"),
    CONVERSION("Conversión"),
    PENALTY("Penal"),
    DROP_GOAL("Drop"),
    YELLOW_CARD("Tarjeta Amarilla"),
    RED_CARD("Tarjeta Roja"),
    SUBSTITUTION("Sustitución")
}

enum class ThemeMode(val label: String) {
    AUTO("Automático"),
    DARK("Oscuro"),
    LIGHT("Claro")
}

enum class AppSection(val label: String) {
    INICIO("Inicio"),
    VIVO("Partidos en vivo"),
    FIXTURE("Fixture y resultados"),
    TABLAS("Tabla de posiciones"),
    EQUIPOS("Equipos y planteles"),
    ESTADISTICAS("Estadísticas"),
    NOTICIAS("Noticias"),
    CONFIGURACION("Configuración"),
    FUENTES("Estado de fuentes")
}

data class MatchEvent(
    val id: String,
    val minute: Int,
    val period: String,
    val type: MatchEventType,
    val teamSide: String, // "home" | "away"
    val player: String,
    val detail: String
)

data class LiveMatchStats(
    val possessionHomePct: Int? = null,
    val possessionAwayPct: Int? = null,
    val territoryHomePct: Int? = null,
    val territoryAwayPct: Int? = null,
    val tacklesHome: Int? = null,
    val tacklesAway: Int? = null,
    val lineoutsWonHome: Int? = null,
    val lineoutsWonAway: Int? = null,
    val scrumsWonHome: Int? = null,
    val scrumsWonAway: Int? = null,
    val penaltiesHome: Int? = null,
    val penaltiesAway: Int? = null
) {
    fun hasAnyMetric(): Boolean = listOf(
        possessionHomePct, territoryHomePct, tacklesHome,
        lineoutsWonHome, scrumsWonHome, penaltiesHome
    ).any { it != null }
}

data class RugbyMatch(
    val id: String,
    val source: String,
    val leagueId: String,
    val leagueName: String,
    val leagueShort: String,
    val phase: String,
    val group: String,
    val category: String,
    val status: MatchStatus,
    val minuteDisplay: String,
    val periodDisplay: String,
    val dateArg: String,
    val dayArg: String,
    val timeArg: String,
    val timestampSec: Long,
    val venueName: String,
    val venueCity: String,
    val homeName: String,
    val homeAbbr: String,
    val homeCrestUrl: String,
    val homeFlagUrl: String,
    val homeCoach: String,
    val homeRank: Int?,
    val homeScore: Int?,
    val awayName: String,
    val awayAbbr: String,
    val awayCrestUrl: String,
    val awayFlagUrl: String,
    val awayCoach: String,
    val awayRank: Int?,
    val awayScore: Int?,
    val isArgentina: Boolean,
    val verificationStatus: VerificationStatus,
    val verifiedSources: List<String>,
    val events: List<MatchEvent>,
    val liveStats: LiveMatchStats?
)

data class CompetitionItem(
    val id: String,
    val name: String,
    val region: String,
    val category: String,
    val teamsCount: Int,
    val season: String
)

data class StandingRow(
    val pos: Int,
    val teamName: String,
    val crestUrl: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val pf: Int,
    val pc: Int,
    val diff: Int,
    val bonus: Int,
    val pts: Int
)

data class TournamentStandings(
    val id: String,
    val name: String,
    val shortName: String,
    val group: String,
    val rows: List<StandingRow>
)

data class PlayerProfile(
    val jersey: String,
    val name: String,
    val position: String,
    val posCode: String,
    val starter: Boolean,
    val captain: Boolean,
    val age: Int?,
    val height: String?,
    val weight: String?,
    val caps: Int?
)

data class TeamProfile(
    val key: String,
    val name: String,
    val crestUrl: String,
    val flagUrl: String,
    val coach: String,
    val stadium: String,
    val worldRank: Int?,
    val groups: List<String>,
    val leagues: List<String>,
    val division: String,
    val roster: List<PlayerProfile>
)

data class WorldRankingRow(
    val pos: Int,
    val prevPos: Int,
    val teamName: String,
    val abbr: String,
    val points: Double,
    val crestUrl: String,
    val flagUrl: String,
    val isWomen: Boolean = false
)

data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val imageUrl: String,
    val category: String,
    val publishedLabel: String,
    val savedOffline: Boolean = false
)

data class SourceHealthItem(
    val id: String,
    val name: String,
    val tier: Int,
    val statusLabel: String,
    val isHealthy: Boolean,
    val latencyMs: Long,
    val detail: String,
    val checkedAtArg: String
)

data class AppConfig(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val favoriteTeam: String = "Argentina",
    val liveRefreshSec: Int = 30,
    val apiTimeoutMs: Long = 10000L,
    val notifyTries: Boolean = true,
    val notifyRedCards: Boolean = true,
    val notifyFullTime: Boolean = true
)

data class LiveNotificationAlert(
    val uniqueKey: String,
    val title: String,
    val body: String,
    val channelType: String // "TRY" | "RED" | "FT"
)

data class TeamComparisonResult(
    val left: TeamProfile,
    val right: TeamProfile,
    val leftWins: Int,
    val leftPlayed: Int,
    val leftPointsFor: Int,
    val leftPointsAgainst: Int,
    val rightWins: Int,
    val rightPlayed: Int,
    val rightPointsFor: Int,
    val rightPointsAgainst: Int
)

data class PlayerComparisonResult(
    val leftTeam: String,
    val left: PlayerProfile,
    val rightTeam: String,
    val right: PlayerProfile
)

data class RugbyDashboard(
    val generatedAtArg: String,
    val nextArgentinaXV: RugbyMatch?,
    val nextArgentina7s: RugbyMatch?,
    val coachXV: String,
    val coach7s: String,
    val matches: List<RugbyMatch>,
    val competitions: List<CompetitionItem>,
    val standings: List<TournamentStandings>,
    val teams: List<TeamProfile>,
    val rankingsMen: List<WorldRankingRow>,
    val rankingsWomen: List<WorldRankingRow>,
    val news: List<NewsArticle>,
    val sourcesHealth: List<SourceHealthItem>,
    val isOfflineFallback: Boolean
)
