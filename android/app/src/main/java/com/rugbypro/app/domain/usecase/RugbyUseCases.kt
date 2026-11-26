package com.rugbypro.app.domain.usecase

import com.rugbypro.app.domain.model.LiveNotificationAlert
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.PlayerComparisonResult
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamComparisonResult
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.VerificationStatus

object CrossVerifyMatchesUseCase {
    fun normalizeTeam(raw: String): String {
        return raw.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]"), "")
    }

    fun matchSignature(m: RugbyMatch): String {
        val a = normalizeTeam(m.homeName)
        val b = normalizeTeam(m.awayName)
        val pair = if (a <= b) "${a}_$b" else "${b}_$a"
        return "${m.dayArg}_$pair"
    }

    /**
     * Cross-validates primary matches against secondary live feeds.
     * Two independent sources agreeing on score & status -> VERIFIED.
     * Otherwise preserves PENDING_VERIFICATION (or LOCAL_CACHE when offline).
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
                        status = if (peer.status == MatchStatus.LIVE || peer.status == MatchStatus.FINISHED) peer.status else m.status,
                        homeScore = peer.homeScore ?: m.homeScore,
                        awayScore = peer.awayScore ?: m.awayScore,
                        minuteDisplay = peer.minuteDisplay.ifBlank { m.minuteDisplay },
                        periodDisplay = peer.periodDisplay.ifBlank { m.periodDisplay },
                        events = if (peer.events.isNotEmpty()) peer.events else m.events,
                        liveStats = if (peer.liveStats?.hasAnyMetric() == true) peer.liveStats else m.liveStats,
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
                merged.add(extra.copy(verificationStatus = VerificationStatus.PENDING_VERIFICATION))
            }
        }
        return merged.sortedWith(
            compareBy<RugbyMatch> {
                when (it.status) {
                    MatchStatus.LIVE -> 0
                    MatchStatus.SCHEDULED -> 1
                    MatchStatus.FINISHED -> 2
                }
            }.thenBy { if (it.status == MatchStatus.FINISHED) -it.timestampSec else it.timestampSec }
        )
    }
}

object FilterMatchesUseCase {
    fun execute(
        matches: List<RugbyMatch>,
        groupFilter: String?,
        statusFilter: MatchStatus?,
        query: String
    ): List<RugbyMatch> {
        val q = query.trim().lowercase()
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
            matchGroup && matchStatus && matchQuery
        }
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
        left: PlayerProfile,
        rightTeam: String,
        right: PlayerProfile
    ): PlayerComparisonResult = PlayerComparisonResult(leftTeam, left, rightTeam, right)
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
