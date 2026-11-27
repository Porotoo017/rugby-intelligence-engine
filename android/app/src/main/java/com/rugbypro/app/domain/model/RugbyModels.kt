package com.rugbypro.app.domain.model

enum class MatchStatus(val label: String) {
    LIVE("EN VIVO"),
    SCHEDULED("PROGRAMADO"),
    FINISHED("FINALIZADO"),
    POSTPONED("POSTERGADO"),
    CANCELLED("CANCELADO")
}

// Uso estrictamente interno de validación cruzada (nunca expuesto en UI)
enum class VerificationStatus {
    VERIFIED,
    PENDING_VERIFICATION,
    LOCAL_CACHE
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
    COMPARACION("Comparación"),
    NOTICIAS("Noticias"),
    CONFIGURACION("Configuración")
}

enum class ActiveKitRole(val label: String) {
    HOME("Titular"),
    AWAY("Suplente"),
    UNKNOWN("Información no disponible")
}

enum class MatchDayPart(val label: String) {
    DIA("Día"),
    TARDE("Tarde"),
    NOCHE("Noche"),
    DESCONOCIDO("Horario N/D")
}

enum class ThermalZone(val label: String) {
    FRIO("Frío"),
    TEMPLADO("Templado"),
    CALOR("Calor")
}

data class MatchWeather(
    val tempC: Double? = null,
    val feelsLikeC: Double? = null,
    val conditionLabel: String = "Clima no disponible",
    val conditionCode: String = "unknown", // "sun" | "cloudy" | "rain" | "drizzle" | "storm" | "snow" | "wind" | "unknown"
    val rainProbabilityPct: Int? = null,
    val windSpeedKmh: Double? = null,
    val windDirection: String? = null,
    val humidityPct: Int? = null,
    val dayPart: MatchDayPart = MatchDayPart.DESCONOCIDO,
    val thermalZone: ThermalZone? = null,
    val updatedAtLabel: String = "",
    val isAvailable: Boolean = false
)

data class PreMatchScheduleTarget(
    val matchId: String,
    val matchTitle: String,
    val kickoffMs: Long,
    val triggerAtMs: Long
)

data class MatchOfficial(
    val name: String,
    val role: String,
    val country: String = ""
)

data class TeamKit(
    val verified: Boolean = true,
    val primaryHex: String,
    val secondaryHex: String,
    val shortsHex: String,
    val pattern: String = "solid", // "solid" | "hoops" | "stripes" | "sash" | "split"
    val label: String = ""
)

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
    val liveStats: LiveMatchStats?,
    val homeKit: TeamKit? = null,
    val awayKit: TeamKit? = null,
    val homeActiveKitRole: ActiveKitRole = ActiveKitRole.UNKNOWN,
    val awayActiveKitRole: ActiveKitRole = ActiveKitRole.UNKNOWN,
    val weather: MatchWeather? = null,
    val statusNote: String? = null,
    val isExtraTime: Boolean = false,
    val refereeName: String = "",
    val officials: List<MatchOfficial> = emptyList(),
    val homeLineup: List<PlayerProfile> = emptyList(),
    val awayLineup: List<PlayerProfile> = emptyList()
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
    val flagUrl: String = "",
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
    val caps: Int?,
    val photoUrl: String? = null,
    val nationality: String? = null,
    val nationalityFlagUrl: String? = null,
    val tries: Int? = null,
    val points: Int? = null,
    val tackles: Int? = null,
    val extraInfo: String? = null
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
    val roster: List<PlayerProfile>,
    val homeKit: TeamKit? = null,
    val awayKit: TeamKit? = null,
    val historicalPlayed: Int? = null,
    val historicalWins: Int? = null,
    val historicalDraws: Int? = null,
    val historicalLosses: Int? = null
)

data class PlayerSearchEntry(
    val teamKey: String,
    val teamName: String,
    val teamCrestUrl: String,
    val teamFlagUrl: String,
    val player: PlayerProfile,
    val teamKit: TeamKit? = null
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
    val source: String, // uso interno solamente; nunca mostrar en UI
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
    val notifyFullTime: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val liveTutorialSeen: Boolean = false
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
    val leftFlagUrl: String,
    val left: PlayerProfile,
    val rightTeam: String,
    val rightFlagUrl: String,
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
