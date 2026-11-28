package com.rugbypro.app.domain.usecase

import com.rugbypro.app.domain.model.ActiveKitRole
import com.rugbypro.app.domain.model.LiveNotificationAlert
import com.rugbypro.app.domain.model.MatchDayPart
import com.rugbypro.app.domain.model.MatchWeather
import com.rugbypro.app.domain.model.PreMatchScheduleTarget
import com.rugbypro.app.domain.model.ThermalZone
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.PlayerComparisonResult
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.PlayerSearchEntry
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamComparisonResult
import com.rugbypro.app.domain.model.TeamKit
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.sqrt

object CrossVerifyMatchesUseCase {

    fun normalizeTeam(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9]"), "")
        .replace("rugby", "")
        .replace("club", "")
        .replace("7s", "")
        .take(16)

    fun matchSignature(match: RugbyMatch): String {
        val h = normalizeTeam(match.homeName)
        val a = normalizeTeam(match.awayName)
        val day = match.dateArg.take(10).ifBlank { match.dayArg.take(10) }
        val is7s = match.group.equals("sevens", true) ||
            match.homeName.contains("7s", true) ||
            match.awayName.contains("7s", true) ||
            match.leagueName.contains("sevens", true) ||
            match.leagueName.contains("svns", true)
        val tag = if (is7s) "_7s" else ""
        return "${h}_vs_${a}_$day$tag"
    }

    /**
     * Validación cruzada interna entre niveles de datos:
     * Si dos fuentes independientes coinciden en marcador y estado -> VERIFIED.
     */
    fun execute(
        primary: List<RugbyMatch>,
        secondary: List<RugbyMatch>,
        fromLocalCacheOnly: Boolean = false
    ): List<RugbyMatch> {
        if (fromLocalCacheOnly && secondary.isEmpty()) {
            return primary.map { it.copy(verificationStatus = VerificationStatus.LOCAL_CACHE) }
        }
        val secondaryBySig = secondary.associateBy { matchSignature(it) }
        val merged = mutableListOf<RugbyMatch>()
        val seenSigs = mutableSetOf<String>()

        for (m in primary) {
            val sig = matchSignature(m)
            seenSigs.add(sig)
            val peer = secondaryBySig[sig]
            if (peer != null) {
                val scoresAgree = (m.homeScore == peer.homeScore && m.awayScore == peer.awayScore)
                val statusAgrees = (m.status == peer.status)
                val sources = (m.verifiedSources + peer.verifiedSources + listOf(m.source, peer.source)).distinct()
                val verified = scoresAgree && statusAgrees && sources.size >= 2
                merged.add(
                    m.copy(
                        id = if (m.id.startsWith("wr:") && peer.id.startsWith("wr_")) peer.id else m.id,
                        group = if (peer.group == "sevens" || m.group == "sevens") "sevens" else m.group,
                        status = if (peer.status == MatchStatus.LIVE || peer.status == MatchStatus.FINISHED) peer.status else m.status,
                        homeScore = run {
                            val mTot = (m.homeScore ?: 0) + (m.awayScore ?: 0)
                            val pTot = (peer.homeScore ?: -1) + (peer.awayScore ?: -1)
                            if (peer.status == MatchStatus.FINISHED || peer.homeScore != null && (m.status != MatchStatus.LIVE || pTot >= mTot)) peer.homeScore else (m.homeScore ?: peer.homeScore)
                        },
                        awayScore = run {
                            val mTot = (m.homeScore ?: 0) + (m.awayScore ?: 0)
                            val pTot = (peer.homeScore ?: -1) + (peer.awayScore ?: -1)
                            if (peer.status == MatchStatus.FINISHED || peer.awayScore != null && (m.status != MatchStatus.LIVE || pTot >= mTot)) peer.awayScore else (m.awayScore ?: peer.awayScore)
                        },
                        minuteDisplay = peer.minuteDisplay.ifBlank { m.minuteDisplay },
                        periodDisplay = peer.periodDisplay.ifBlank { m.periodDisplay },
                        clockSeconds = if (peer.clockSyncedAtMs >= m.clockSyncedAtMs && peer.clockSeconds != null) peer.clockSeconds else (peer.clockSeconds ?: m.clockSeconds),
                        clockSyncedAtMs = maxOf(peer.clockSyncedAtMs, m.clockSyncedAtMs),
                        clockRunning = if (peer.clockSyncedAtMs >= m.clockSyncedAtMs) peer.clockRunning else m.clockRunning,
                        events = if (peer.events.isNotEmpty()) peer.events else m.events,
                        liveStats = if (peer.liveStats?.hasAnyMetric() == true) peer.liveStats else m.liveStats,
                        weather = m.weather ?: peer.weather,
                        statusNote = peer.statusNote ?: m.statusNote,
                        isExtraTime = m.isExtraTime || peer.isExtraTime,
                        refereeName = peer.refereeName.ifBlank { m.refereeName },
                        officials = if (peer.officials.isNotEmpty()) peer.officials else m.officials,
                        homeLineup = if (peer.homeLineup.isNotEmpty()) peer.homeLineup else m.homeLineup,
                        awayLineup = if (peer.awayLineup.isNotEmpty()) peer.awayLineup else m.awayLineup,
                        verificationStatus = if (verified) VerificationStatus.VERIFIED else VerificationStatus.PENDING_VERIFICATION,
                        verifiedSources = sources
                    )
                )
            } else {
                merged.add(
                    m.copy(
                        verificationStatus = if (m.verifiedSources.distinct().size >= 2) {
                            VerificationStatus.VERIFIED
                        } else {
                            VerificationStatus.PENDING_VERIFICATION
                        }
                    )
                )
            }
        }

        for (extra in secondary) {
            val sig = matchSignature(extra)
            if (sig !in seenSigs) {
                seenSigs.add(sig)
                merged.add(extra.copy(verificationStatus = VerificationStatus.PENDING_VERIFICATION))
            }
        }

        return merged.sortedWith(
            compareBy<RugbyMatch> {
                when (it.status) {
                    MatchStatus.LIVE -> 0
                    MatchStatus.SCHEDULED -> 1
                    MatchStatus.POSTPONED -> 2
                    MatchStatus.CANCELLED -> 3
                    MatchStatus.FINISHED -> 4
                }
            }.thenBy { if (it.status == MatchStatus.FINISHED) -it.timestampSec else it.timestampSec }
        )
    }
}

/**
 * Regla estricta de Partidos en Vivo:
 * Solo devuelve partidos jugándose ahora (LIVE) o próximos a jugarse en el día actual (SCHEDULED hoy).
 * Prohibido incluir partidos FINALIZADOS.
 */
object FilterLiveAndTodayMatchesUseCase {
    private fun todayKeysArg(nowMs: Long): Set<String> {
        val tz = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
        val d = Date(nowMs)
        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }.format(d)
        val latam = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = tz }.format(d)
        val shortDm = SimpleDateFormat("dd/MM", Locale.US).apply { timeZone = tz }.format(d)
        return setOf(iso, latam, shortDm)
    }

    fun isScheduledToday(match: RugbyMatch, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (match.status != MatchStatus.SCHEDULED) return false
        val keys = todayKeysArg(nowMs)
        val hay = "${match.dateArg} ${match.dayArg}".trim()
        if (keys.any { k -> hay.contains(k, ignoreCase = true) }) return true
        if (match.timestampSec > 0L) {
            val tz = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
            val matchDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
                .format(Date(match.timestampSec * 1000L))
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
                .format(Date(nowMs))
            return matchDay == todayIso
        }
        return false
    }

    fun liveNow(matches: List<RugbyMatch>): List<RugbyMatch> =
        matches.filter { it.status == MatchStatus.LIVE }

    fun upcomingToday(matches: List<RugbyMatch>, nowMs: Long = System.currentTimeMillis()): List<RugbyMatch> =
        matches.filter { it.status == MatchStatus.SCHEDULED && isScheduledToday(it, nowMs) }
            .sortedBy { it.timestampSec }
}

object ResolveMatchKitsUseCase {
    private fun parseRgb(hex: String?): Triple<Int, Int, Int>? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        return runCatching {
            Triple(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
        }.getOrNull()
    }

    private fun colorDistance(hexA: String?, hexB: String?): Double {
        val a = parseRgb(hexA) ?: return 999.0
        val b = parseRgb(hexB) ?: return 999.0
        val dr = (a.first - b.first).toDouble()
        val dg = (a.second - b.second).toDouble()
        val db = (a.third - b.third).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    fun resolveForMatch(
        homeTeam: TeamProfile?,
        awayTeam: TeamProfile?
    ): ResolvedMatchKits {
        val hHome = homeTeam?.homeKit?.takeIf { it.verified }
        val hAway = homeTeam?.awayKit?.takeIf { it.verified }
        val (homeRole, activeHomeKit) = when {
            hHome != null -> ActiveKitRole.HOME to hHome
            hAway != null -> ActiveKitRole.AWAY to hAway
            else -> ActiveKitRole.UNKNOWN to null
        }
        val aHome = awayTeam?.homeKit?.takeIf { it.verified }
        val aAway = awayTeam?.awayKit?.takeIf { it.verified }
        val clash = activeHomeKit != null && aHome != null && colorDistance(activeHomeKit.primaryHex, aHome.primaryHex) < 115.0
        val (awayRole, activeAwayKit) = when {
            clash && aAway != null -> ActiveKitRole.AWAY to aAway
            aHome != null -> ActiveKitRole.HOME to aHome
            aAway != null -> ActiveKitRole.AWAY to aAway
            else -> ActiveKitRole.UNKNOWN to null
        }
        return ResolvedMatchKits(
            homeKit = activeHomeKit,
            awayKit = activeAwayKit,
            homeRole = homeRole,
            awayRole = awayRole
        )
    }

    data class ResolvedMatchKits(
        val homeKit: TeamKit?,
        val awayKit: TeamKit?,
        val homeRole: ActiveKitRole,
        val awayRole: ActiveKitRole
    )
}

object FilterMatchesUseCase {
    fun execute(
        matches: List<RugbyMatch>,
        groupFilter: String?,
        statusFilter: MatchStatus?,
        query: String,
        dateQuery: String = ""
    ): List<RugbyMatch> {
        val q = query.trim().lowercase()
        val dq = dateQuery.trim().lowercase()
        return matches.filter { m ->
            val matchGroup = groupFilter.isNullOrBlank() ||
                groupFilter == "todos" ||
                (groupFilter == "argentina" && m.isArgentina) ||
                m.group.equals(groupFilter, ignoreCase = true) ||
                m.leagueId.equals(groupFilter, ignoreCase = true) ||
                m.leagueShort.equals(groupFilter, ignoreCase = true)
            val matchStatus = statusFilter == null || m.status == statusFilter
            val matchQuery = q.isEmpty() ||
                m.homeName.lowercase().contains(q) ||
                m.awayName.lowercase().contains(q) ||
                m.leagueName.lowercase().contains(q) ||
                m.venueCity.lowercase().contains(q)
            val matchDate = dq.isEmpty() ||
                m.dateArg.lowercase().contains(dq) ||
                m.dayArg.lowercase().contains(dq)
            matchGroup && matchStatus && matchQuery && matchDate
        }
    }
}

object SearchTeamsAndPlayersUseCase {
    fun searchTeams(
        teams: List<TeamProfile>,
        query: String,
        countryOrGroupFilter: String = "Todos"
    ): List<TeamProfile> {
        val q = query.trim().lowercase()
        val f = countryOrGroupFilter.trim().lowercase()
        return teams.filter { t ->
            val matchCountry = f == "todos" || f.isEmpty() ||
                t.name.lowercase().contains(f) ||
                t.groups.any { it.equals(f, true) } ||
                t.leagues.any { it.lowercase().contains(f) }
            val matchQuery = q.isEmpty() ||
                t.name.lowercase().contains(q) ||
                t.coach.lowercase().contains(q) ||
                t.division.lowercase().contains(q)
            matchCountry && matchQuery
        }
    }

    fun searchPlayers(
        teams: List<TeamProfile>,
        query: String,
        countryFilter: String = "Todos",
        teamFilter: String = "Todos",
        positionFilter: String = "Todas"
    ): List<PlayerSearchEntry> {
        val q = query.trim().lowercase()
        val cf = countryFilter.trim().lowercase()
        val tf = teamFilter.trim().lowercase()
        val pf = positionFilter.trim().lowercase()

        val result = mutableListOf<PlayerSearchEntry>()
        for (t in teams) {
            if (tf != "todos" && tf.isNotEmpty() && !t.name.equals(teamFilter, ignoreCase = true)) continue
            for (p in t.roster) {
                val nat = (p.nationality ?: t.name).lowercase()
                val matchCountry = cf == "todos" || cf.isEmpty() || nat.contains(cf) || t.name.lowercase().contains(cf)
                val matchPos = pf == "todas" || pf.isEmpty() ||
                    p.position.lowercase().contains(pf) ||
                    p.posCode.lowercase().contains(pf)
                val matchQuery = q.isEmpty() ||
                    p.name.lowercase().contains(q) ||
                    p.position.lowercase().contains(q) ||
                    t.name.lowercase().contains(q) ||
                    p.jersey.lowercase() == q
                if (matchCountry && matchPos && matchQuery) {
                    result.add(
                        PlayerSearchEntry(
                            teamKey = t.key,
                            teamName = t.name,
                            teamCrestUrl = t.crestUrl,
                            teamFlagUrl = p.nationalityFlagUrl ?: t.flagUrl,
                            player = p,
                            teamKit = t.homeKit ?: t.awayKit
                        )
                    )
                }
            }
        }
        return result
    }
}

object CompareTeamsUseCase {
    fun execute(
        left: TeamProfile,
        right: TeamProfile,
        matches: List<RugbyMatch>
    ): TeamComparisonResult {
        fun calcStats(teamName: String): Triple<Int, Int, Pair<Int, Int>> {
            val norm = CrossVerifyMatchesUseCase.normalizeTeam(teamName)
            var played = 0
            var wins = 0
            var pf = 0
            var pc = 0
            for (m in matches) {
                if (m.status != MatchStatus.FINISHED || m.homeScore == null || m.awayScore == null) continue
                val hMatch = CrossVerifyMatchesUseCase.normalizeTeam(m.homeName) == norm
                val aMatch = CrossVerifyMatchesUseCase.normalizeTeam(m.awayName) == norm
                if (hMatch) {
                    played++
                    pf += m.homeScore
                    pc += m.awayScore
                    if (m.homeScore > m.awayScore) wins++
                } else if (aMatch) {
                    played++
                    pf += m.awayScore
                    pc += m.homeScore
                    if (m.awayScore > m.homeScore) wins++
                }
            }
            return Triple(wins, played, pf to pc)
        }
        val (lw, lp, lpts) = calcStats(left.name)
        val (rw, rp, rpts) = calcStats(right.name)
        return TeamComparisonResult(
            left = left,
            right = right,
            leftWins = lw,
            leftPlayed = lp,
            leftPointsFor = lpts.first,
            leftPointsAgainst = lpts.second,
            rightWins = rw,
            rightPlayed = rp,
            rightPointsFor = rpts.first,
            rightPointsAgainst = rpts.second
        )
    }
}

object ComparePlayersUseCase {
    fun execute(
        leftTeam: String,
        leftFlagUrl: String,
        left: PlayerProfile,
        rightTeam: String,
        rightFlagUrl: String,
        right: PlayerProfile
    ): PlayerComparisonResult = PlayerComparisonResult(
        leftTeam = leftTeam,
        leftFlagUrl = leftFlagUrl,
        left = left,
        rightTeam = rightTeam,
        rightFlagUrl = rightFlagUrl,
        right = right
    )
}

object DetectKeyLiveEventsUseCase {
    fun execute(
        previous: List<RugbyMatch>,
        current: List<RugbyMatch>,
        notifyTries: Boolean,
        notifyRedCards: Boolean,
        notifyFullTime: Boolean
    ): List<LiveNotificationAlert> {
        val prevById = previous.associateBy { it.id }
        val alerts = mutableListOf<LiveNotificationAlert>()
        for (cur in current) {
            val prev = prevById[cur.id]
            val scoreHeader = "${cur.homeName} ${cur.homeScore ?: 0} - ${cur.awayScore ?: 0} ${cur.awayName}"
            if (prev != null && prev.status == MatchStatus.LIVE && cur.status == MatchStatus.FINISHED && notifyFullTime) {
                alerts.add(
                    LiveNotificationAlert(
                        uniqueKey = "ft_${cur.id}",
                        title = "Final del partido · ${cur.leagueShort}",
                        body = scoreHeader,
                        channelType = "FT"
                    )
                )
            }
            val knownEventIds = prev?.events?.map { it.id }?.toSet().orEmpty()
            for (ev in cur.events) {
                if (ev.id in knownEventIds) continue
                if (ev.type == MatchEventType.TRY && notifyTries) {
                    alerts.add(
                        LiveNotificationAlert(
                            uniqueKey = "ev_${cur.id}_${ev.id}",
                            title = "¡TRY! ${cur.leagueShort} (${ev.minute}')",
                            body = "${ev.player.ifBlank { "Jugador" }} · $scoreHeader",
                            channelType = "TRY"
                        )
                    )
                } else if (ev.type == MatchEventType.RED_CARD && notifyRedCards) {
                    alerts.add(
                        LiveNotificationAlert(
                            uniqueKey = "ev_${cur.id}_${ev.id}",
                            title = "Tarjeta Roja · ${cur.leagueShort} (${ev.minute}')",
                            body = "${ev.player.ifBlank { "Jugador" }} · $scoreHeader",
                            channelType = "RED"
                        )
                    )
                }
            }
        }
        return alerts
    }
}


object RugbyDateUtils {
    private val MONTH_MAP = mapOf(
        "ene" to 1, "jan" to 1, "feb" to 2, "mar" to 3, "abr" to 4, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "ago" to 8, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dic" to 12, "dec" to 12
    )
    private val ISO_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}:\\d{2}))?.*")
    private val TEXT_DATE_REGEX = Regex("(\\d{1,2})\\s+([A-Za-zÁÉÍÓÚáéíóú]{3})(?:\\s+(\\d{4}))?(?:\\s*[·,]\\s*(\\d{2}:\\d{2}))?")

    fun formatLocalKickoff(timestampSec: Long, fallbackDateArg: String): String {
        if (timestampSec <= 0L) return normalizeDisplayDate(fallbackDateArg).ifBlank { "Horario por confirmar" }
        return runCatching {
            val zone = java.time.ZoneId.systemDefault()
            val dt = java.time.Instant.ofEpochSecond(timestampSec).atZone(zone)
            dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.getDefault()))
        }.getOrElse { normalizeDisplayDate(fallbackDateArg) }
    }

    fun formatLocalDateOnly(timestampSec: Long, fallbackDateArg: String = ""): String {
        if (timestampSec <= 0L) {
            return normalizeDisplayDate(fallbackDateArg).substringBefore(" ·").ifBlank { "Por confirmar" }
        }
        return runCatching {
            val zone = java.time.ZoneId.systemDefault()
            val dt = java.time.Instant.ofEpochSecond(timestampSec).atZone(zone)
            dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault()))
        }.getOrElse { normalizeDisplayDate(fallbackDateArg).substringBefore(" ·") }
    }

    fun normalizeDisplayDate(raw: String): String {
        val clean = raw.replace(" ART", "").trim()
        if (clean.isBlank()) return ""
        ISO_REGEX.matchEntire(clean)?.let { m ->
            val dmy = "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
            val hm = m.groupValues[4]
            return if (hm.isNotBlank()) "$dmy · $hm" else dmy
        }
        TEXT_DATE_REGEX.find(clean)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return clean
            val mon = MONTH_MAP[m.groupValues[2].lowercase()] ?: return clean
            val year = m.groupValues[3].toIntOrNull() ?: java.time.LocalDate.now().year
            val dmy = String.format(Locale.US, "%02d/%02d/%04d", day, mon, year)
            val hm = m.groupValues[4]
            return if (hm.isNotBlank()) "$dmy · $hm" else dmy
        }
        return clean
    }

    fun inferDayPart(timestampSec: Long, fallbackTimeArg: String = ""): MatchDayPart {
        val hour = if (timestampSec > 0L) {
            runCatching {
                java.time.Instant.ofEpochSecond(timestampSec)
                    .atZone(java.time.ZoneId.systemDefault())
                    .hour
            }.getOrNull()
        } else {
            fallbackTimeArg.substringBefore(":").trim().toIntOrNull()
        }
        return when (hour) {
            null -> MatchDayPart.DESCONOCIDO
            in 6..12 -> MatchDayPart.DIA
            in 13..18 -> MatchDayPart.TARDE
            else -> MatchDayPart.NOCHE
        }
    }

    fun formatPercent(value: Double?): String =
        if (value == null) "No disponible" else String.format(Locale.US, "%.1f%%", value)
}

object WeatherClassificationUseCase {
    fun thermalZoneFor(tempC: Double?): ThermalZone? = when {
        tempC == null -> null
        tempC < 13.0 -> ThermalZone.FRIO
        tempC < 25.0 -> ThermalZone.TEMPLADO
        else -> ThermalZone.CALOR
    }

    fun windCompass(degrees: Double?): String? {
        if (degrees == null) return null
        val dirs = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val idx = (((degrees % 360.0) + 360.0) % 360.0 / 45.0).let { kotlin.math.round(it).toInt() % 8 }
        return dirs[idx]
    }

    fun mapWmoCondition(wmoCode: Int?, windSpeedKmh: Double?): Pair<String, String> {
        if (wmoCode == null) return "Clima no disponible" to "unknown"
        return when (wmoCode) {
            0 -> if ((windSpeedKmh ?: 0.0) >= 34.0) "Soleado con viento" to "wind" else "Despejado" to "sun"
            1, 2 -> "Parcialmente nublado" to "cloudy"
            3, 45, 48 -> "Nublado" to "cloudy"
            51, 53, 55, 56, 57 -> "Llovizna" to "drizzle"
            61, 63, 65, 66, 67, 80, 81, 82 -> "Lluvia" to "rain"
            71, 73, 75, 77, 85, 86 -> "Nieve" to "snow"
            95, 96, 99 -> "Tormenta" to "storm"
            else -> if ((windSpeedKmh ?: 0.0) >= 34.0) "Viento fuerte" to "wind" else "Variable" to "cloudy"
        }
    }
}

object PreMatchWindowUseCase {
    private const val PRELOAD_LEAD_MS = 30L * 60L * 1000L
    private const val MAX_HORIZON_MS = 48L * 60L * 60L * 1000L

    fun computeTargets(
        matches: List<RugbyMatch>,
        nowMs: Long = System.currentTimeMillis()
    ): List<PreMatchScheduleTarget> {
        return matches.mapNotNull { m ->
            if (m.status != MatchStatus.SCHEDULED || m.timestampSec <= 0L) return@mapNotNull null
            val kickoffMs = m.timestampSec * 1000L
            if (kickoffMs <= nowMs || (kickoffMs - nowMs) > MAX_HORIZON_MS) return@mapNotNull null
            val triggerAt = (kickoffMs - PRELOAD_LEAD_MS).coerceAtLeast(nowMs)
            PreMatchScheduleTarget(
                matchId = m.id,
                matchTitle = "${m.homeName} vs ${m.awayName}",
                kickoffMs = kickoffMs,
                triggerAtMs = triggerAt
            )
        }.sortedBy { it.triggerAtMs }
    }

    fun isInsideThirtyMinuteWindow(match: RugbyMatch, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (match.status != MatchStatus.SCHEDULED || match.timestampSec <= 0L) return false
        val kickoffMs = match.timestampSec * 1000L
        val diff = kickoffMs - nowMs
        return diff in 1L..PRELOAD_LEAD_MS
    }
}
